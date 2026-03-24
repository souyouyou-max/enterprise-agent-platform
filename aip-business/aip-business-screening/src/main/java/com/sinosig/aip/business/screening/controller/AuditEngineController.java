package com.sinosig.aip.business.screening.controller;

import com.sinosig.aip.business.screening.service.AuditEngineService;
import com.sinosig.aip.common.core.response.ResponseResult;
import com.sinosig.aip.data.entity.ClueResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审计引擎 REST API
 *
 * <p>提供手动触发、数据同步、规则执行和疑点查询等接口。
 * <p>同时提供 {@code /trigger/{applyCode}} 路径变量风格端点，
 * 供 aip-scheduler 通过 Feign 调用（避免调度服务直连数据库）。
 */
@Slf4j
@Tag(name = "规则SQL审计引擎", description = "定时任务驱动的规则SQL分析引擎 - 数据同步/规则执行/疑点查询")
@RestController
@RequestMapping("/api/v1/audit-engine")
@RequiredArgsConstructor
public class AuditEngineController {

    private final AuditEngineService auditEngineService;

    // ─── aip-scheduler Feign 调用入口（路径变量风格） ────────────────────────

    /**
     * 供 aip-scheduler 通过 Feign 调用：触发指定申请编码的完整审计
     * 路径：POST /api/v1/audit-engine/trigger/{applyCode}
     */
    @Operation(summary = "触发审计（Feign入口）",
            description = "aip-scheduler 定时任务通过此接口触发审计，applyCode 即申请编码。")
    @PostMapping("/trigger/{applyCode}")
    public ResponseResult<AuditSummaryResponse> triggerByPath(
            @Parameter(description = "申请编码") @PathVariable("applyCode") String applyCode) {
        log.info("[AuditEngineController] 收到 Feign 审计触发，申请编码：{}", applyCode);
        List<ClueResult> clues = auditEngineService.fullAudit(applyCode);
        AuditSummaryResponse summary = AuditSummaryResponse.from(applyCode, clues);
        log.info("[AuditEngineController] 申请[{}]审计完成，共{}条疑点", applyCode, summary.getTotalClues());
        return ResponseResult.success(summary);
    }

    // ─── 手动调用接口（Query Param 风格） ─────────────────────────────────────

    @Operation(summary = "手动触发全量审计",
            description = "立即对指定申请执行完整审计流程：数据同步 → 规则检测 → 疑点入库。")
    @PostMapping("/trigger")
    public ResponseResult<Map<String, Object>> triggerFullAudit(
            @Parameter(description = "申请编码") @RequestParam String applyCode) {
        List<ClueResult> clues = auditEngineService.fullAudit(applyCode);
        long high   = clues.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        long medium = clues.stream().filter(c -> "MEDIUM".equals(c.getRiskLevel())).count();
        return ResponseResult.success(Map.of(
                "applyCode",   applyCode,
                "totalClues",  clues.size(),
                "highRisk",    high,
                "mediumRisk",  medium,
                "clues",       clues
        ));
    }

    @Operation(summary = "仅同步外部数据",
            description = "触发所有外部系统数据适配器同步数据到本地仓库，不执行规则检测。")
    @PostMapping("/sync")
    public ResponseResult<String> syncData() {
        auditEngineService.syncAllDataSources();
        return ResponseResult.success("数据同步完成");
    }

    @Operation(summary = "仅执行规则检测",
            description = "对指定申请执行4条审计规则，使用本地仓库现有数据（不重新同步）。")
    @PostMapping("/run-rules")
    public ResponseResult<List<ClueResult>> runRules(
            @Parameter(description = "申请编码") @RequestParam String applyCode) {
        return ResponseResult.success(auditEngineService.runAllRules(applyCode));
    }

    @Operation(summary = "查询待处理疑点")
    @GetMapping("/clues/pending")
    public ResponseResult<List<ClueResult>> getPendingClues(
            @Parameter(description = "申请编码") @RequestParam String applyCode) {
        return ResponseResult.success(auditEngineService.getPendingClues(applyCode));
    }

    @Operation(summary = "查询全部疑点")
    @GetMapping("/clues")
    public ResponseResult<List<ClueResult>> getAllClues(
            @Parameter(description = "申请编码") @RequestParam String applyCode) {
        return ResponseResult.success(auditEngineService.getAllClues(applyCode));
    }

    // ─── Response DTO（Feign 端点响应，字段与 AuditSummaryDTO 对齐） ──────────

    @Data
    public static class AuditSummaryResponse {
        private String orgCode;       // 保持字段名与 AuditSummaryDTO 一致，Feign 反序列化时使用
        private int    totalClues;
        private long   highRisk;
        private long   mediumRisk;
        private long   lowRisk;

        public static AuditSummaryResponse from(String applyCode, List<ClueResult> clues) {
            AuditSummaryResponse r = new AuditSummaryResponse();
            r.orgCode    = applyCode;
            r.totalClues = clues.size();
            r.highRisk   = clues.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
            r.mediumRisk = clues.stream().filter(c -> "MEDIUM".equals(c.getRiskLevel())).count();
            r.lowRisk    = clues.stream().filter(c -> "LOW".equals(c.getRiskLevel())).count();
            return r;
        }
    }
}
