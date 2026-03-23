package com.enterprise.agent.app.ops.service;

import com.enterprise.agent.business.pipeline.config.EapPipelineProperties;
import com.enterprise.agent.business.pipeline.config.PipelineEffectiveConfig;
import com.enterprise.agent.business.pipeline.config.PipelineEffectiveConfigResolver;
import com.enterprise.agent.data.entity.OcrFileMain;
import com.enterprise.agent.data.entity.OcrPipelineBatch;
import com.enterprise.agent.data.mapper.OcrPipelineBatchMapper;
import com.enterprise.agent.data.service.OcrFileDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一期运维剧本服务（面向 OCR 流水线）。
 *
 * <p>目标：将人工排障中最常见的 3 步固化为 API：
 * 1) 批次状态诊断
 * 2) 配置预检（按 extra_info 合并后的有效配置）
 * 3) 输出可执行建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOpsPlaybookService {

    private final OcrPipelineBatchMapper batchMapper;
    private final OcrFileDataService ocrFileDataService;
    private final PipelineEffectiveConfigResolver effectiveConfigResolver;
    private final EapPipelineProperties pipelineProperties;
    private final NacosMcpClient nacosMcpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();
    private static final Set<String> CHANGE_WHITELIST = Set.of(
            "eap.pipeline.ocr.enabled",
            "eap.pipeline.analysis.enabled",
            "eap.pipeline.analysis.max-images-per-file",
            "eap.pipeline.analysis.max-total-analysis-pages",
            "eap.pipeline.compare.enabled",
            "eap.pipeline.scheduler.enabled",
            "eap.pipeline.scheduler.interval-ms",
            "eap.pipeline.scheduler.pending-stale-seconds",
            "eap.pipeline.scheduler.processing-stale-seconds",
            "eap.pipeline.scheduler.done-stale-seconds"
    );

    public PipelineDiagnosisReport diagnose(String batchNo) {
        OcrPipelineBatch batch = batchMapper.findByBatchNo(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("批次不存在: " + batchNo);
        }

        List<OcrFileMain> mains = ocrFileDataService.findMainByBatchNo(batchNo);
        PipelineEffectiveConfig effective = effectiveConfigResolver.resolve(batch.getExtraInfo());
        BatchFileStats stats = buildStats(mains);
        List<DiagnosisFinding> findings = new ArrayList<>();

        addStaleFindings(batch, findings);
        addStatusFindings(batch, stats, effective, findings);
        addConfigFindings(effective, findings);

        Map<String, Object> configSnapshot = new LinkedHashMap<>();
        configSnapshot.put("ocrEnabled", effective.ocrEnabled());
        configSnapshot.put("analysisEnabled", effective.analysisEnabled());
        configSnapshot.put("compareEnabled", effective.compareEnabled());
        configSnapshot.put("maxImagesPerFile", effective.maxImagesPerFile());
        configSnapshot.put("maxTotalAnalysisPages", effective.maxTotalAnalysisPages());
        configSnapshot.put("failToleranceRatio", effective.failToleranceRatio());
        configSnapshot.put("multimodalTemplateKeys", effective.multimodalTemplateKeys());

        return new PipelineDiagnosisReport(
                batch.getBatchNo(),
                batch.getStatus(),
                batch.getUpdatedAt(),
                stats,
                configSnapshot,
                findings
        );
    }

    public ConfigPrecheckReport precheck(String extraInfo) {
        PipelineEffectiveConfig effective = effectiveConfigResolver.resolve(extraInfo);
        List<DiagnosisFinding> findings = new ArrayList<>();

        addConfigFindings(effective, findings);
        if (!effective.ocrEnabled() && (effective.analysisEnabled() || effective.compareEnabled())) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "PIPELINE_ORDER_RISK",
                    "当前配置关闭 OCR，但开启了后续阶段；新提交批次将无法按预期自动推进。",
                    "若用于常规提交链路，请启用 OCR；若仅用于补偿/手动触发，请在工单中备注。"
            ));
        }

        boolean passed = findings.stream().noneMatch(f -> "HIGH".equals(f.severity()));
        return new ConfigPrecheckReport(passed, effective, findings);
    }

    public ConfigChangePlan buildChangePlan(ConfigChangePlanRequest request) {
        ConfigPrecheckReport precheck = precheck(request.extraInfo());
        List<DiagnosisFinding> findings = new ArrayList<>(precheck.findings());
        List<ChangeValidation> validations = validateChangeWhitelistAndRanges(request.proposedChanges(), findings);

        NacosContext nacosContext = null;
        if (request.dataId() != null && !request.dataId().isBlank()) {
            NacosMcpClient.McpCallResult cfg = nacosMcpClient.getConfig(request.namespace(), request.group(), request.dataId());
            NacosMcpClient.McpCallResult history = nacosMcpClient.getConfigHistory(request.namespace(), request.group(), request.dataId());
            nacosContext = new NacosContext(
                    nacosMcpClient.enabled(),
                    request.namespace(),
                    request.group() == null || request.group().isBlank() ? "DEFAULT_GROUP" : request.group(),
                    request.dataId(),
                    cfg.success(),
                    cfg.payload(),
                    history.success(),
                    history.payload(),
                    mergeMcpMessages(cfg.message(), history.message())
            );
            if (!cfg.success()) {
                findings.add(new DiagnosisFinding(
                        "MEDIUM",
                        "NACOS_MCP_CONFIG_UNAVAILABLE",
                        "未能从 Nacos MCP 读取当前配置。",
                        "检查 eap.ops.agent.nacos-mcp 配置或 MCP 服务可用性。"
                ));
            }
        }

        boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equals(f.severity()));
        String decision = hasHigh ? "REJECT" : "REVIEW";
        String summary = hasHigh
                ? "存在高风险项，建议拒绝直接发布，先修正后再审批。"
                : "未发现高风险阻断项，建议人工复核后发布。";
        return new ConfigChangePlan(null, decision, summary, precheck, validations, nacosContext, findings);
    }

    public ApprovalTicket submitForApproval(ConfigChangePlanRequest request, String submitter, String ticketNo) {
        ConfigChangePlan plan = buildChangePlan(request);
        String planId = "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String status = "REJECT".equals(plan.decision()) ? "REJECTED" : "PENDING_APPROVAL";
        ConfigChangePlan planWithId = new ConfigChangePlan(
                planId,
                plan.decision(),
                plan.summary(),
                plan.precheck(),
                plan.validations(),
                plan.nacosContext(),
                plan.findings()
        );
        if ("PENDING_APPROVAL".equals(status)) {
            pendingApprovals.put(planId, new PendingApproval(planWithId, request, submitter, ticketNo, LocalDateTime.now()));
        }
        return new ApprovalTicket(
                planId,
                status,
                submitter,
                ticketNo,
                LocalDateTime.now(),
                planWithId
        );
    }

    public PublishResult approveAndPublish(String planId, String approver, String comment) {
        PendingApproval pending = pendingApprovals.get(planId);
        if (pending == null) {
            throw new IllegalArgumentException("审批单不存在或已处理: " + planId);
        }
        ConfigChangePlan plan = pending.plan();
        if ("REJECT".equals(plan.decision())) {
            pendingApprovals.remove(planId);
            return new PublishResult(planId, false, "当前计划为 REJECT，不允许发布。", null);
        }

        ConfigChangePlanRequest req = pending.request();
        NacosMcpClient.McpCallResult publish = nacosMcpClient.publishConfig(
                req.namespace(),
                req.group(),
                req.dataId(),
                toJson(req.proposedChanges()),
                "approver=" + safe(approver) + ", comment=" + safe(comment)
        );
        if (publish.success()) {
            pendingApprovals.remove(planId);
            return new PublishResult(planId, true, "发布成功", publish.payload());
        }
        return new PublishResult(planId, false, "发布失败: " + safe(publish.message()), publish.payload());
    }

    private void addStaleFindings(OcrPipelineBatch batch, List<DiagnosisFinding> findings) {
        if (batch.getUpdatedAt() == null) {
            return;
        }
        long idleSeconds = Duration.between(batch.getUpdatedAt(), LocalDateTime.now()).getSeconds();
        int pendingStale = pipelineProperties.getScheduler().getPendingStaleSeconds();
        int processingStale = pipelineProperties.getScheduler().getProcessingStaleSeconds();
        int doneStale = pipelineProperties.getScheduler().getDoneStaleSeconds();

        String status = batch.getStatus();
        if (OcrPipelineBatch.STATUS_PENDING.equals(status) && idleSeconds > pendingStale) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "PENDING_STALE",
                    "批次处于 PENDING 且超过阈值未推进（idle=" + idleSeconds + "s）。",
                    "检查提交链路与调度补偿；可手动触发 /trigger-ocr。"
            ));
        } else if (OcrPipelineBatch.STATUS_OCR_PROCESSING.equals(status) && idleSeconds > processingStale) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "OCR_PROCESSING_STALE",
                    "批次 OCR_PROCESSING 超时未更新（idle=" + idleSeconds + "s）。",
                    "检查 OCR 外部服务连通性与单文件失败日志，必要时重试文件或补偿批次。"
            ));
        } else if ((OcrPipelineBatch.STATUS_OCR_DONE.equals(status)
                || OcrPipelineBatch.STATUS_PARTIAL_FAIL.equals(status)
                || OcrPipelineBatch.STATUS_ANALYZED.equals(status)) && idleSeconds > doneStale) {
            findings.add(new DiagnosisFinding(
                    "MEDIUM",
                    "STAGE_NOT_ADVANCED",
                    "批次在中间完成态停留过久（status=" + status + ", idle=" + idleSeconds + "s）。",
                    "检查 analysis/compare 开关与下游服务状态，必要时手动触发下一阶段。"
            ));
        }
    }

    private void addStatusFindings(OcrPipelineBatch batch,
                                   BatchFileStats stats,
                                   PipelineEffectiveConfig effective,
                                   List<DiagnosisFinding> findings) {
        if (OcrPipelineBatch.STATUS_FAILED.equals(batch.getStatus())) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "BATCH_FAILED",
                    "批次已失败，errorMessage=" + safe(batch.getErrorMessage()),
                    "优先查看失败文件与下游依赖日志，确认后可按文件重试或重新提交批次。"
            ));
        }
        if (stats.total() > 0 && stats.ocrFailed() == stats.total()) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "ALL_OCR_FAILED",
                    "批次全部文件 OCR 失败。",
                    "检查 OCR 服务配置/网络/鉴权；确认输入文件格式与大小。"
            ));
        }
        if (!effective.analysisEnabled() && OcrPipelineBatch.STATUS_OCR_DONE.equals(batch.getStatus())) {
            findings.add(new DiagnosisFinding(
                    "LOW",
                    "ANALYSIS_DISABLED",
                    "分析阶段已关闭，OCR 完成后不会进入语义分析。",
                    "如需结构化字段，请开启 analysis.enabled。"
            ));
        }
        if (!effective.compareEnabled() && OcrPipelineBatch.STATUS_ANALYZED.equals(batch.getStatus())) {
            findings.add(new DiagnosisFinding(
                    "LOW",
                    "COMPARE_DISABLED",
                    "对比阶段已关闭，分析完成后批次将直接结束。",
                    "如需文件相似度结果，请开启 compare.enabled。"
            ));
        }
    }

    private void addConfigFindings(PipelineEffectiveConfig effective, List<DiagnosisFinding> findings) {
        if (effective.maxImagesPerFile() < 1 || effective.maxImagesPerFile() > 16) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "MAX_IMAGES_PER_FILE_OUT_OF_RANGE",
                    "maxImagesPerFile=" + effective.maxImagesPerFile() + " 超出推荐范围 [1,16]。",
                    "建议设置为 1~8；页数较多文档优先提升总页限制而非单次图片数。"
            ));
        }
        if (effective.maxTotalAnalysisPages() < 1 || effective.maxTotalAnalysisPages() > 500) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "MAX_TOTAL_ANALYSIS_PAGES_OUT_OF_RANGE",
                    "maxTotalAnalysisPages=" + effective.maxTotalAnalysisPages() + " 超出建议范围 [1,500]。",
                    "建议设置为 20~200；极端大值可能引发成本和时延失控。"
            ));
        }
        if (effective.failToleranceRatio() < 0 || effective.failToleranceRatio() > 1) {
            findings.add(new DiagnosisFinding(
                    "HIGH",
                    "FAIL_TOLERANCE_OUT_OF_RANGE",
                    "failToleranceRatio=" + effective.failToleranceRatio() + " 非法，应在 [0,1]。",
                    "请修正为 0~1 之间的小数。"
            ));
        } else if (effective.failToleranceRatio() > 0.8) {
            findings.add(new DiagnosisFinding(
                    "MEDIUM",
                    "FAIL_TOLERANCE_TOO_HIGH",
                    "failToleranceRatio=" + effective.failToleranceRatio() + " 偏高，可能掩盖失败文件。",
                    "建议生产设置在 0.3~0.6 区间。"
            ));
        }
    }

    private BatchFileStats buildStats(List<OcrFileMain> mains) {
        int ocrSuccess = 0;
        int ocrFailed = 0;
        int analysisSuccess = 0;
        int analysisFailed = 0;
        for (OcrFileMain main : mains) {
            if (OcrFileDataService.STATUS_SUCCESS.equals(main.getOcrStatus())) {
                ocrSuccess++;
            } else if (OcrFileDataService.STATUS_FAILED.equals(main.getOcrStatus())) {
                ocrFailed++;
            }
            if ("SUCCESS".equals(main.getAnalysisStatus()) || "SKIPPED".equals(main.getAnalysisStatus())) {
                analysisSuccess++;
            } else if ("FAILED".equals(main.getAnalysisStatus())) {
                analysisFailed++;
            }
        }
        return new BatchFileStats(mains.size(), ocrSuccess, ocrFailed, analysisSuccess, analysisFailed);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private List<ChangeValidation> validateChangeWhitelistAndRanges(Map<String, Object> proposedChanges,
                                                                    List<DiagnosisFinding> findings) {
        List<ChangeValidation> validations = new ArrayList<>();
        if (proposedChanges == null || proposedChanges.isEmpty()) {
            findings.add(new DiagnosisFinding(
                    "MEDIUM",
                    "EMPTY_CHANGE_SET",
                    "未提供 proposedChanges，无法生成有效变更计划。",
                    "请提供拟变更的配置键值。"
            ));
            return validations;
        }

        for (Map.Entry<String, Object> e : proposedChanges.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            boolean allowed = CHANGE_WHITELIST.contains(key);
            String severity = allowed ? "LOW" : "HIGH";
            String message = allowed ? "白名单校验通过" : "非白名单键，不允许自动发布";
            String suggestion = allowed ? "可继续做人工复核与灰度发布。" : "请走人工评审或扩展白名单后再发布。";

            if (allowed && "eap.pipeline.analysis.max-images-per-file".equals(key)) {
                Integer iv = toInteger(value);
                if (iv == null || iv < 1 || iv > 16) {
                    severity = "HIGH";
                    message = "max-images-per-file 需在 [1,16]";
                    suggestion = "建议设置在 1~8 区间。";
                }
            }
            if (allowed && "eap.pipeline.analysis.max-total-analysis-pages".equals(key)) {
                Integer iv = toInteger(value);
                if (iv == null || iv < 1 || iv > 500) {
                    severity = "HIGH";
                    message = "max-total-analysis-pages 需在 [1,500]";
                    suggestion = "建议设置在 20~200 区间。";
                }
            }
            if (allowed && "eap.pipeline.scheduler.interval-ms".equals(key)) {
                Integer iv = toInteger(value);
                if (iv == null || iv < 10000 || iv > 3600000) {
                    severity = "HIGH";
                    message = "scheduler.interval-ms 需在 [10000,3600000]";
                    suggestion = "建议设置在 30000~120000。";
                }
            }

            if ("HIGH".equals(severity)) {
                findings.add(new DiagnosisFinding(
                        "HIGH",
                        "CHANGE_VALIDATION_FAILED",
                        "配置项 " + key + " 校验失败: " + message,
                        suggestion
                ));
            }
            validations.add(new ChangeValidation(key, value, allowed, severity, message, suggestion));
        }
        return validations;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String mergeMcpMessages(String a, String b) {
        Set<String> set = new LinkedHashSet<>();
        if (a != null && !a.isBlank()) set.add(a.trim());
        if (b != null && !b.isBlank()) set.add(b.trim());
        return set.isEmpty() ? null : String.join("; ", set);
    }

    private JsonNode toJson(Map<String, Object> map) {
        if (map == null) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
        return objectMapper.valueToTree(map);
    }

    public record PipelineDiagnosisReport(
            String batchNo,
            String batchStatus,
            LocalDateTime updatedAt,
            BatchFileStats fileStats,
            Map<String, Object> configSnapshot,
            List<DiagnosisFinding> findings
    ) {}

    public record BatchFileStats(
            int total,
            int ocrSuccess,
            int ocrFailed,
            int analysisSuccess,
            int analysisFailed
    ) {}

    public record DiagnosisFinding(
            String severity,
            String code,
            String message,
            String suggestion
    ) {}

    public record ConfigPrecheckReport(
            boolean passed,
            PipelineEffectiveConfig effectiveConfig,
            List<DiagnosisFinding> findings
    ) {}

    public record ConfigChangePlanRequest(
            String namespace,
            String group,
            String dataId,
            String extraInfo,
            Map<String, Object> proposedChanges
    ) {}

    public record ChangeValidation(
            String key,
            Object value,
            boolean allowed,
            String severity,
            String message,
            String suggestion
    ) {}

    public record NacosContext(
            boolean mcpEnabled,
            String namespace,
            String group,
            String dataId,
            boolean currentConfigReadOk,
            JsonNode currentConfigPayload,
            boolean historyReadOk,
            JsonNode historyPayload,
            String mcpMessage
    ) {}

    public record ConfigChangePlan(
            String planId,
            String decision,
            String summary,
            ConfigPrecheckReport precheck,
            List<ChangeValidation> validations,
            NacosContext nacosContext,
            List<DiagnosisFinding> findings
    ) {}

    public record ApprovalTicket(
            String planId,
            String status,
            String submitter,
            String ticketNo,
            LocalDateTime createdAt,
            ConfigChangePlan plan
    ) {}

    public record PublishResult(
            String planId,
            boolean success,
            String message,
            JsonNode payload
    ) {}

    private record PendingApproval(
            ConfigChangePlan plan,
            ConfigChangePlanRequest request,
            String submitter,
            String ticketNo,
            LocalDateTime createdAt
    ) {}
}
