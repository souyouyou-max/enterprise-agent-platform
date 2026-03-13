package com.enterprise.agent.impl.interaction.toolkit;

import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.core.dispatcher.AgentDispatcher;
import com.enterprise.agent.data.entity.AgentTask;
import com.enterprise.agent.data.service.AgentTaskDataService;
import com.enterprise.agent.impl.insight.InsightAgent;
import com.enterprise.agent.insight.model.InsightResult;
import com.enterprise.agent.knowledge.service.KnowledgeQaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * InteractionToolkit - 交互中心 Agent 专属技能集
 * <p>
 * 将各专业 Agent 和业务服务封装为可供 LLM 调用的工具，
 * 实现 LLM 自主决策路由（替代硬编码 switch-case 意图识别）。
 * <p>
 * 注意：agentDispatcher 通过构造器注入，无循环依赖风险，
 * 因为 AgentDispatcher 内部的 agents 列表使用 @Lazy 延迟注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionToolkit {

    private final AgentDispatcher agentDispatcher;
    private final KnowledgeQaService knowledgeQaService;
    private final InsightAgent insightAgent;
    private final AgentTaskDataService agentTaskDataService;

    /**
     * 启动完整 Planner→Executor→Reviewer→Communicator 业务分析流水线
     */
    @Tool(description = "启动完整的多步骤业务分析流水线（规划→执行→审查→报告），适用于复杂业务目标，" +
            "如：分析销售数据并生成报告、执行多维度分析任务等。返回完整的分析报告。")
    public String runFullPipeline(String goal) {
        log.info("[InteractionToolkit] runFullPipeline, goal={}", goal.substring(0, Math.min(50, goal.length())));
        AgentResult result = agentDispatcher.runPipeline(goal);
        if (result.isSuccess()) {
            return result.getOutput();
        }
        return "流水线执行未完全成功，部分结果：" + result.getOutput();
    }

    /**
     * 查询企业知识库（RAG）
     */
    @Tool(description = "查询企业内部知识库，回答与公司规章制度、内部流程、HR政策等相关的问题，" +
            "如：年假申请流程、报销政策、入职手续等。")
    public String queryKnowledgeBase(String question) {
        log.info("[InteractionToolkit] queryKnowledgeBase, question={}", question.substring(0, Math.min(50, question.length())));
        return knowledgeQaService.answer(question);
    }

    /**
     * 调用数据洞察 Agent 分析业务数据（NL2BI）
     */
    @Tool(description = "分析企业业务数据，支持自然语言数据查询和洞察，" +
            "如：哪个部门销售额最高、本季度增长趋势、客户分布情况等。返回分析结论和执行的SQL。")
    public String analyzeBusinessData(String question) {
        log.info("[InteractionToolkit] analyzeBusinessData, question={}", question.substring(0, Math.min(50, question.length())));
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

    /**
     * 查询任务执行状态
     */
    @Tool(description = "根据任务ID查询Agent任务的执行状态（PENDING/EXECUTING/COMPLETED/FAILED）和基本信息。" +
            "taskId为系统分配的任务唯一标识数字。")
    public String getTaskStatus(Long taskId) {
        log.info("[InteractionToolkit] getTaskStatus, taskId={}", taskId);
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
