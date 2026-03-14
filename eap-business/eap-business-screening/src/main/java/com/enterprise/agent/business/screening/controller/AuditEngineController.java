package com.enterprise.agent.business.screening.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.business.screening.service.AuditEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审计引擎 REST API
 *
 * <p>提供手动触发、数据同步、规则执行和疑点查询等接口，
 * 补充 eap-scheduler 定时触发之外的按需审计能力。
 */
@Tag(name = "规则SQL审计引擎", description = "定时任务驱动的规则SQL分析引擎 - 数据同步/规则执行/疑点查询")
@RestController
@RequestMapping("/api/v1/audit-engine")
@RequiredArgsConstructor
public class AuditEngineController {

    private final AuditEngineService auditEngineService;

    @Operation(summary = "手动触发全量审计",
            description = "立即对指定机构执行完整审计流程：数据同步（4个外部系统）→ 规则检测（4条规则）→ 疑点入库。" +
                    "适用于临时稽查或验证规则效果。")
    @PostMapping("/trigger")
    public ResponseResult<Map<String, Object>> triggerFullAudit(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        List<ClueResult> clues = auditEngineService.fullAudit(orgCode);

        long high = clues.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        long medium = clues.stream().filter(c -> "MEDIUM".equals(c.getRiskLevel())).count();

        return ResponseResult.success(Map.of(
                "orgCode", orgCode,
                "totalClues", clues.size(),
                "highRisk", high,
                "mediumRisk", medium,
                "clues", clues
        ));
    }

    @Operation(summary = "仅同步外部数据",
            description = "触发所有外部系统数据适配器（招采/费控/EHR/企查查）同步数据到本地仓库，不执行规则检测。")
    @PostMapping("/sync")
    public ResponseResult<String> syncData() {
        auditEngineService.syncAllDataSources();
        return ResponseResult.success("数据同步完成");
    }

    @Operation(summary = "仅执行规则检测",
            description = "对指定机构执行4条审计规则（大额未招标/化整为零/围标串标/利益冲突），" +
                    "使用本地仓库现有数据（不重新同步）。适用于已有数据的快速复检。")
    @PostMapping("/run-rules")
    public ResponseResult<List<ClueResult>> runRules(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        List<ClueResult> clues = auditEngineService.runAllRules(orgCode);
        return ResponseResult.success(clues);
    }

    @Operation(summary = "查询待处理疑点",
            description = "查询指定机构所有状态为 PENDING 的疑点线索，供 Agent 读取后进行智能分析。")
    @GetMapping("/clues/pending")
    public ResponseResult<List<ClueResult>> getPendingClues(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        return ResponseResult.success(auditEngineService.getPendingClues(orgCode));
    }

    @Operation(summary = "查询全部疑点",
            description = "查询指定机构所有疑点线索记录（含PENDING/CONFIRMED/DISMISSED），按时间倒序排列。")
    @GetMapping("/clues")
    public ResponseResult<List<ClueResult>> getAllClues(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        return ResponseResult.success(auditEngineService.getAllClues(orgCode));
    }
}
