package com.enterprise.agent.core.orchestrator;

import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AgentOrchestrator - Agent 编排器
 * 支持 runPipeline: Planner → Executor → Reviewer → Communicator
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final Map<AgentRole, BaseAgent> agentRegistry;

    public AgentOrchestrator(List<BaseAgent> agents) {
        this.agentRegistry = agents.stream()
                .collect(Collectors.toMap(BaseAgent::getRole, Function.identity()));
        log.info("AgentOrchestrator 已注册 {} 个 Agent: {}", agents.size(),
                agents.stream().map(a -> a.getRole().name()).collect(Collectors.joining(", ")));
    }

    /**
     * 执行完整 Pipeline: Planner → Executor → Reviewer（可重试）→ Communicator
     *
     * @param context         任务上下文
     * @param maxReviewRetries Reviewer 质量不达标时最多重试执行次数
     * @return 最终的 AgentResult（来自 Communicator）
     */
    public AgentResult runPipeline(AgentContext context, int maxReviewRetries) {
        log.info("[Orchestrator] 开始 Pipeline, taskId={}", context.getTaskId());

        // Step 1: Planner
        AgentResult plannerResult = runAgent(AgentRole.PLANNER, context);
        if (!plannerResult.isSuccess()) {
            log.error("[Orchestrator] Planner 失败，终止 Pipeline");
            return plannerResult;
        }

        // Step 2: Executor + Reviewer（支持重试）
        int reviewAttempt = 0;
        AgentResult reviewerResult;
        do {
            if (reviewAttempt > 0) {
                log.warn("[Orchestrator] 质量不达标，第 {} 次重新执行 Executor", reviewAttempt);
                context.getReviewIssues().clear();
            }

            AgentResult executorResult = runAgent(AgentRole.EXECUTOR, context);
            if (!executorResult.isSuccess()) {
                log.error("[Orchestrator] Executor 失败，终止 Pipeline");
                return executorResult;
            }

            reviewerResult = runAgent(AgentRole.REVIEWER, context);
            reviewAttempt++;

        } while (!context.isReviewPassed() && reviewAttempt < maxReviewRetries);

        if (!context.isReviewPassed()) {
            log.warn("[Orchestrator] 达到最大重试次数 {}，继续生成报告", maxReviewRetries);
        }

        // Step 3: Communicator
        AgentResult communicatorResult = runAgent(AgentRole.COMMUNICATOR, context);
        log.info("[Orchestrator] Pipeline 完成, taskId={}, 最终评分={}",
                context.getTaskId(), context.getReviewScore());
        return communicatorResult;
    }

    /**
     * 执行单个 Agent
     */
    public AgentResult runAgent(AgentRole role, AgentContext context) {
        BaseAgent agent = agentRegistry.get(role);
        if (agent == null) {
            String msg = "未找到 Agent: " + role;
            log.error("[Orchestrator] {}", msg);
            return AgentResult.failure(role, msg);
        }
        try {
            return agent.execute(context);
        } catch (Exception e) {
            log.error("[Orchestrator] Agent {} 执行异常: {}", role, e.getMessage(), e);
            return AgentResult.failure(role, e.getMessage());
        }
    }
}
