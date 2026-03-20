package com.enterprise.agent.business.chat.toolkit;

import com.enterprise.agent.business.chat.InsightAgent;
import com.enterprise.agent.common.core.enums.ReportStyle;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.core.orchestrator.AgentOrchestrator;
import com.enterprise.agent.data.entity.AgentTask;
import com.enterprise.agent.data.service.AgentTaskDataService;
import com.enterprise.agent.dataservice.insight.model.InsightResult;
import com.enterprise.agent.dataservice.knowledge.service.KnowledgeQaService;
import com.enterprise.agent.engine.agent.clue.ClueDiscoveryAgent;
import com.enterprise.agent.engine.agent.monitor.MonitoringAgent;
import com.enterprise.agent.engine.agent.risk.RiskAnalysisAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * AgentOrchestrationToolkit - 交互中心 LLM 可用工具全集
 * <p>
 * 将所有业务能力封装为 {@code @Tool} 方法，统一注册给 InteractionCenterAgent 的 ChatClient，
 * 由 LLM 自主决策路由，替代硬编码意图识别。
 * <p>
 * 包含以下能力组：
 * <ul>
 *   <li>机构风控三件套：线索发现 / 风险透视 / 监测预警</li>
 *   <li>完整 Pipeline：规划→执行→审查→报告</li>
 *   <li>知识库问答（RAG）</li>
 *   <li>数据洞察（NL2BI）</li>
 *   <li>任务状态查询</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrationToolkit {

    private final ClueDiscoveryAgent clueDiscoveryAgent;
    private final RiskAnalysisAgent riskAnalysisAgent;
    private final MonitoringAgent monitoringAgent;
    private final AgentOrchestrator agentOrchestrator;
    private final KnowledgeQaService knowledgeQaService;
    private final InsightAgent insightAgent;
    private final AgentTaskDataService agentTaskDataService;

    // ─────────────────────────────────────────────────────────────────────────
    // 机构风控三件套（走 BaseAgent.execute() 标准链路）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 线索发现：扫描指定机构的采购、财务、合同等数据，发现异常疑点线索。
     */
    @Tool(description = "线索发现：扫描指定机构的采购、财务、合同等数据，识别超付、未招标、利益冲突等违规风险。输入机构编码orgCode（如ORG001）。")
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
     * 风险透视：对指定机构进行经营/合规/财务/采购四维度风险评分，生成综合风险画像报告。
     */
    @Tool(description = "风险透视：对指定机构进行经营/合规/财务/采购四维度风险评分，生成综合风险画像报告。输入机构编码orgCode。")
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
     * 监测预警：检查指定机构各项风险指标是否超过阈值，生成分级预警通知和处置建议。
     */
    @Tool(description = "监测预警：检查指定机构各项风险指标是否超过阈值，生成分级预警通知和处置建议。输入机构编码orgCode。")
    public String checkMonitoring(String orgCode) {
        log.info("[AgentOrchestrationToolkit] checkMonitoring, orgCode={}", orgCode);
        AgentContext ctx = AgentContext.builder()
                .taskId("MONITOR-" + orgCode + "-" + System.currentTimeMillis())
                .goal("请检查机构【" + orgCode + "】当前的监测状态和预警信息。")
                .build();
        AgentResult result = monitoringAgent.execute(ctx);
        return result.isSuccess() ? result.getOutput() : "监测检查失败：" + result.getErrorMessage();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 完整 Pipeline：Planner → Executor → Reviewer → Communicator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 启动完整多步骤业务分析流水线，适用于需要拆解、执行、审查并生成完整报告的复杂目标。
     */
    @Tool(description = "启动完整的多步骤业务分析流水线（规划→执行→审查→报告），适用于复杂业务目标，" +
            "如：分析销售数据并生成报告、执行多维度分析任务等。返回完整的分析报告。")
    public String runFullPipeline(String goal) {
        log.info("[AgentOrchestrationToolkit] runFullPipeline, goal={}", goal.substring(0, Math.min(50, goal.length())));
        AgentContext context = AgentContext.builder()
                .taskId(String.valueOf(System.currentTimeMillis()))
                .taskName("OrchestrationToolkit-Pipeline")
                .goal(goal)
                .reportStyle(ReportStyle.DETAILED)
                .build();
        AgentResult result = agentOrchestrator.runPipeline(context, 2);
        if (result.isSuccess()) {
            return result.getOutput();
        }
        return "流水线执行未完全成功，部分结果：" + result.getOutput();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 知识库 & 数据洞察
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 查询企业内部知识库（RAG），回答规章制度、内部流程、HR政策等问题。
     */
    @Tool(description = "查询企业内部知识库，回答与公司规章制度、内部流程、HR政策等相关的问题，" +
            "如：年假申请流程、报销政策、入职手续等。")
    public String queryKnowledgeBase(String question) {
        log.info("[AgentOrchestrationToolkit] queryKnowledgeBase, question={}", question.substring(0, Math.min(50, question.length())));
        return knowledgeQaService.answer(question);
    }

    /**
     * 调用数据洞察 Agent 分析业务数据（NL2BI），支持自然语言数据查询。
     */
    @Tool(description = "分析企业业务数据，支持自然语言数据查询和洞察，" +
            "如：哪个部门销售额最高、本季度增长趋势、客户分布情况等。返回分析结论和执行的SQL。")
    public String analyzeBusinessData(String question) {
        log.info("[AgentOrchestrationToolkit] analyzeBusinessData, question={}", question.substring(0, Math.min(50, question.length())));
        InsightResult result = insightAgent.investigate(question);
        if (!result.isSuccess()) {
            return "数据分析失败：" + result.getErrorMessage();
        }
        StringBuilder sb = new StringBuilder();
        if (result.getAnalysis() != null) {
            sb.append(result.getAnalysis());
        }
        if (result.getGeneratedSql() != null) {
            sb.append("\n\n> 执行SQL：`").append(result.getGeneratedSql()).append("`");
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 任务管理
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 根据任务 ID 查询 Agent 任务的执行状态和基本信息。
     */
    @Tool(description = "根据任务ID查询Agent任务的执行状态（PENDING/EXECUTING/COMPLETED/FAILED）和基本信息。" +
            "taskId为系统分配的任务唯一标识数字。")
    public String getTaskStatus(Long taskId) {
        log.info("[AgentOrchestrationToolkit] getTaskStatus, taskId={}", taskId);
        AgentTask task = agentTaskDataService.getById(taskId);
        if (task == null) {
            return String.format("{\"found\":false,\"taskId\":%d,\"message\":\"任务不存在\"}", taskId);
        }
        return String.format(
                "{\"found\":true,\"taskId\":%d,\"taskName\":\"%s\",\"status\":\"%s\"," +
                        "\"reviewerScore\":%s,\"createdAt\":\"%s\",\"updatedAt\":\"%s\"}",
                task.getId(),
                task.getTaskName(),
                task.getStatus(),
                task.getReviewerScore() != null ? task.getReviewerScore() : "null",
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
