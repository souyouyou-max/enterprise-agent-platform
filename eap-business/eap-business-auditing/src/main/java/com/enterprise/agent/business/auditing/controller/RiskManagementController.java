package com.enterprise.agent.business.auditing.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.business.auditing.clue.ClueDiscoveryAgent;
import com.enterprise.agent.business.auditing.monitor.MonitoringAgent;
import com.enterprise.agent.business.auditing.risk.RiskAnalysisAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RiskManagementController - 机构风险远程管理 REST API
 * <p>
 * 对外暴露线索发现、风险透视、监测预警及全量稽核四个接口，
 * 支撑机构风险远程管理机器人的前端交互。
 */
@Tag(name = "机构风险远程管理", description = "机构风险远程管理机器人 - 线索发现 / 风险透视 / 监测预警 / 全量稽核")
@RestController
@RequestMapping("/api/v1/risk-management")
@RequiredArgsConstructor
public class RiskManagementController {

    private final ClueDiscoveryAgent clueDiscoveryAgent;
    private final RiskAnalysisAgent riskAnalysisAgent;
    private final MonitoringAgent monitoringAgent;

    @Operation(summary = "线索发现扫描",
            description = "扫描机构各审计主题（采购/财务/合同）的疑点线索。" +
                    "topic 可选值：procurement / finance / contract；不传则执行全量扫描。")
    @PostMapping("/clue/scan")
    public ResponseResult<AgentResult> scanClues(
            @Parameter(description = "机构编码") @RequestParam String orgCode,
            @Parameter(description = "审计主题（可选）：procurement / finance / contract")
            @RequestParam(required = false) String topic) {
        AgentResult result = (topic != null && !topic.isBlank())
                ? clueDiscoveryAgent.scanByTopic(orgCode, topic)
                : clueDiscoveryAgent.scanAll(orgCode);
        return ResponseResult.success(result);
    }

    @Operation(summary = "机构风险透视分析",
            description = "对指定机构进行经营/合规/财务/采购四维度风险评分，生成综合风险画像报告。")
    @PostMapping("/risk/analyze")
    public ResponseResult<AgentResult> analyzeRisk(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        AgentResult result = riskAnalysisAgent.analyzeOrgRisk(orgCode);
        return ResponseResult.success(result);
    }

    @Operation(summary = "监测预警检查",
            description = "检查机构各项风险指标是否超过阈值，生成分级预警通知（红/橙/黄/绿）和处置建议。")
    @PostMapping("/monitoring/check")
    public ResponseResult<AgentResult> checkMonitoring(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        AgentResult result = monitoringAgent.monitorOrg(orgCode);
        return ResponseResult.success(result);
    }

    @Operation(summary = "全量稽核（调用所有Agent）",
            description = "同时调用线索发现、风险透视、监测预警三个智能体，返回完整的综合稽核报告。")
    @PostMapping("/full-audit")
    public ResponseResult<Map<String, AgentResult>> fullAudit(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        Map<String, AgentResult> results = new LinkedHashMap<>();
        results.put("clueDiscovery", clueDiscoveryAgent.scanAll(orgCode));
        results.put("riskAnalysis", riskAnalysisAgent.analyzeOrgRisk(orgCode));
        results.put("monitoring", monitoringAgent.monitorOrg(orgCode));
        return ResponseResult.success(results);
    }
}
