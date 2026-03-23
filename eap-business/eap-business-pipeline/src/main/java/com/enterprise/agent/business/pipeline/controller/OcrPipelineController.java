package com.enterprise.agent.business.pipeline.controller;

import com.enterprise.agent.business.pipeline.controller.dto.SubmitBatchRequest;
import com.enterprise.agent.business.pipeline.controller.dto.SubmitBatchResponse;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService.BatchProgressView;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService.PipelineFileInfo;
import com.enterprise.agent.common.core.response.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OCR 流水线 REST 控制器
 *
 * <h3>接口清单</h3>
 * <pre>
 * POST /api/v1/enterprise/pipeline/submit-batch      提交多文件批次（启动完整流水线）
 * GET  /api/v1/enterprise/pipeline/batch/{batchNo}   查询批次处理进度
 * POST /api/v1/enterprise/pipeline/batch/{batchNo}/trigger-ocr       手动触发 OCR 阶段
 * POST /api/v1/enterprise/pipeline/batch/{batchNo}/trigger-analysis  手动触发分析阶段
 * POST /api/v1/enterprise/pipeline/batch/{batchNo}/trigger-compare   手动触发对比阶段
 * POST /api/v1/enterprise/pipeline/file/{mainId}/retry-ocr           单文件重试 OCR
 * POST /api/v1/enterprise/pipeline/file/{mainId}/retry-analysis      单文件重试分析
 * </pre>
 */
@Slf4j
@Tag(name = "OCR 流水线", description = "多文件 OCR → 多模态语义分析 → 文件相似度对比 全流程管理")
@RestController
@RequestMapping("/api/v1/enterprise/pipeline")
@RequiredArgsConstructor
public class OcrPipelineController {

    private final OcrPipelineService pipelineService;

    // ──────────────────────────────────────────────────────────────────────
    // 提交批次
    // ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "提交批次文件，启动 OCR 流水线",
               description = """
               流水线阶段：大智部 OCR → 正言语义分析 → Python 相似度对比（可配置跳过）。
               支持单文件（仅识别/多模态）；多文件时可做两两相似度对比。
               各阶段异步执行，通过 batch_no 查询进度。
               """)
    @PostMapping("/submit-batch")
    public ResponseResult<SubmitBatchResponse> submitBatch(@RequestBody SubmitBatchRequest req) {
        // 自动生成 batchNo（若未传）
        if (req.getFiles() == null || req.getFiles().isEmpty()) {
            return ResponseResult.error(400, "请至少上传 1 个文件");
        }

        String batchNo = (req.getBatchNo() != null && !req.getBatchNo().isBlank())
                ? req.getBatchNo()
                : "BATCH_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                  + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<PipelineFileInfo> files = req.getFiles().stream()
                .map(f -> new PipelineFileInfo(
                        f.getFileName(), f.getFileType(), f.getFileSize(),
                        f.getFilePath(), f.getBase64Content(), f.getSha256()))
                .collect(Collectors.toList());

        var batch = pipelineService.submitBatch(batchNo, req.getAppCode(), files, req.getExtraInfo());
        log.info("[PipelineController] 批次提交成功 batchNo={}, files={}", batchNo, files.size());

        String batchId = batch.getId() == null ? null : String.valueOf(batch.getId());
        return ResponseResult.success(new SubmitBatchResponse(batchNo, batchId, batch.getStatus(), batch.getTotalFiles()));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 查询进度
    // ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "查询批次处理进度",
               description = "返回批次状态、各文件 OCR/分析状态、分析结构化结果（印章/营业执照/报价等）及是否已完成相似度对比。")
    @GetMapping("/batch/{batchNo}")
    public ResponseResult<BatchProgressView> getBatchProgress(@PathVariable("batchNo") String batchNo) {
        BatchProgressView view = pipelineService.getBatchProgress(batchNo);
        if (view == null) {
            return ResponseResult.error("批次不存在: " + batchNo);
        }
        return ResponseResult.success(view);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 手动触发各阶段
    // ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "手动触发 OCR 阶段",
               description = "适用于批次卡在 PENDING 状态时手动重触发，定时任务也会自动补偿。")
    @PostMapping("/batch/{batchNo}/trigger-ocr")
    public ResponseResult<String> triggerOcr(@PathVariable("batchNo") String batchNo) {
        pipelineService.triggerOcrPhase(batchNo);
        return ResponseResult.success("OCR 阶段已触发");
    }

    @Operation(summary = "手动触发语义分析阶段",
               description = "适用于批次 OCR 完成但分析未启动时手动重触发。正言大模型提取印章、营业执照、报价金额等结构化字段。")
    @PostMapping("/batch/{batchNo}/trigger-analysis")
    public ResponseResult<String> triggerAnalysis(@PathVariable("batchNo") String batchNo) {
        pipelineService.triggerAnalysisPhase(batchNo);
        return ResponseResult.success("语义分析阶段已触发");
    }

    @Operation(summary = "手动触发相似度对比阶段",
               description = "调用 Python 服务对批次内文件两两对比：文字相似度（TF-IDF）+ 文件整体相似度（SHA-256/感知哈希）。")
    @PostMapping("/batch/{batchNo}/trigger-compare")
    public ResponseResult<String> triggerCompare(@PathVariable("batchNo") String batchNo) {
        pipelineService.triggerComparePhase(batchNo);
        return ResponseResult.success("相似度对比阶段已触发");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 单文件重试
    // ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "单文件重试 OCR", description = "重置文件 OCR 状态并重新识别，不影响批次内其他文件。")
    @PostMapping("/file/{mainId}/retry-ocr")
    public ResponseResult<String> retryOcr(@PathVariable("mainId") Long mainId) {
        pipelineService.retryOcrForFile(mainId);
        return ResponseResult.success("OCR 重试已触发，mainId=" + mainId);
    }

    @Operation(summary = "单文件重试语义分析", description = "删除旧分析结果并重新调用正言大模型分析。")
    @PostMapping("/file/{mainId}/retry-analysis")
    public ResponseResult<String> retryAnalysis(@PathVariable("mainId") Long mainId) {
        pipelineService.retryAnalysisForFile(mainId);
        return ResponseResult.success("语义分析重试已触发，mainId=" + mainId);
    }
}
