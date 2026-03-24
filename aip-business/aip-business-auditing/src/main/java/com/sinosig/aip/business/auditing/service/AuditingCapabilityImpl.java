package com.sinosig.aip.business.auditing.service;

import com.sinosig.aip.business.auditing.clue.ClueDiscoveryAgent;
import com.sinosig.aip.business.auditing.monitor.MonitoringAgent;
import com.sinosig.aip.business.auditing.risk.RiskAnalysisAgent;
import com.sinosig.aip.core.capability.AuditingCapability;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AuditingCapability 的默认实现，统一封装三类机构稽核能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditingCapabilityImpl implements AuditingCapability {

    private final ClueDiscoveryAgent clueDiscoveryAgent;
    private final RiskAnalysisAgent riskAnalysisAgent;
    private final MonitoringAgent monitoringAgent;

    @Override
    public String discoverClues(String orgCode) {
        AgentContext ctx = AgentContext.builder()
                .taskId("CLUE-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请扫描机构【" + orgCode + "】的所有疑点线索，包括采购、财务、合同异常。")
                .build();
        AgentResult result = clueDiscoveryAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "线索发现失败：" + result.getErrorMessage();
    }

    @Override
    public String analyzeRisk(String orgCode) {
        AgentContext ctx = AgentContext.builder()
                .taskId("RISK-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请对机构【" + orgCode + "】进行全面风险透视分析，输出风险评分和综合报告。")
                .build();
        AgentResult result = riskAnalysisAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "风险分析失败：" + result.getErrorMessage();
    }

    @Override
    public String checkMonitoring(String orgCode) {
        AgentContext ctx = AgentContext.builder()
                .taskId("MONITOR-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请检查机构【" + orgCode + "】当前的监测状态和预警信息。")
                .build();
        AgentResult result = monitoringAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "监测检查失败：" + result.getErrorMessage();
    }
}
