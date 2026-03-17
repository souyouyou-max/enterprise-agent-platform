package com.enterprise.agent.business.chat.toolkit;

import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.engine.agent.clue.ClueDiscoveryAgent;
import com.enterprise.agent.engine.agent.monitor.MonitoringAgent;
import com.enterprise.agent.engine.agent.risk.RiskAnalysisAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * AgentOrchestrationToolkit - 以 Agent 为粒度的工具集
 * <p>
 * 将四个业务 Agent 封装为 LLM 可调用的 @Tool 方法，注册给 InteractionCenterAgent 的 ChatClient，
 * 由 LLM 自主决定调用哪些 Agent 及调用顺序，替代硬编码的意图识别路由。
 * <p>
 * 每个工具通过 AgentContext 构造目标描述，统一走 BaseAgent.execute() 链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrationToolkit {

    private final ClueDiscoveryAgent clueDiscoveryAgent;
    private final RiskAnalysisAgent riskAnalysisAgent;
    private final MonitoringAgent monitoringAgent;

    /**
     * 线索发现：扫描指定机构的采购、财务、合同等数据，发现异常疑点线索。
     */
    @Tool(description = "线索发现：扫描指定机构的采购、财务、合同等数据，发现异常疑点线索。输入机构编码orgCode（如ORG001）。")
    public String discoverClues(String orgCode) {
        log.info("[AgentOrchestrationToolkit] discoverClues, orgCode={}", orgCode);
        AgentContext ctx = AgentContext.builder()
                .taskId("CLUE-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请扫描机构【" + orgCode + "】的所有疑点线索，包括采购、财务、合同异常。")
                .build();
        AgentResult result = clueDiscoveryAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "线索发现失败：" + result.getErrorMessage();
    }

    /**
     * 风险透视：对指定机构进行多维风险评分和综合分析，输出风险报告。
     */
    @Tool(description = "风险透视：对指定机构进行多维风险评分和综合分析，输出风险报告。输入机构编码orgCode。")
    public String analyzeRisk(String orgCode) {
        log.info("[AgentOrchestrationToolkit] analyzeRisk, orgCode={}", orgCode);
        AgentContext ctx = AgentContext.builder()
                .taskId("RISK-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请对机构【" + orgCode + "】进行全面风险透视分析，输出风险评分和综合报告。")
                .build();
        AgentResult result = riskAnalysisAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "风险分析失败：" + result.getErrorMessage();
    }

    /**
     * 监测预警：检查指定机构当前的监测状态和预警信息，返回预警列表和建议。
     */
    @Tool(description = "监测预警：检查指定机构当前的监测状态和预警信息，返回预警列表和建议。输入机构编码orgCode。")
    public String checkMonitoring(String orgCode) {
        log.info("[AgentOrchestrationToolkit] checkMonitoring, orgCode={}", orgCode);
        AgentContext ctx = AgentContext.builder()
                .taskId("MONITOR-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请检查机构【" + orgCode + "】当前的监测状态和预警信息。")
                .build();
        AgentResult result = monitoringAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "监测检查失败：" + result.getErrorMessage();
    }

    // 招采稽核能力已移除（相关表不存在）
}
