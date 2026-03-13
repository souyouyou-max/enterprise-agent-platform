package com.enterprise.agent.core.orchestrator;

import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.core.dispatcher.AgentDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AgentOrchestrator - Agent 编排器
 * <p>
 * 通过 AgentDispatcher 执行 Planner → Executor → Reviewer → Communicator 流水线，
 * 支持 Reviewer 质量不达标时重试执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentDispatcher agentDispatcher;

    /**
     * 执行完整 Pipeline：Planner → Executor → Reviewer（可重试）→ Communicator
     *
     * @param context          任务上下文
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
        do {
            if (reviewAttempt > 0) {
                log.warn("[Orchestrator] 质量不达标，第 {} 次重新执行 Executor", reviewAttempt);
                if (context.getReviewIssues() != null) {
                    context.getReviewIssues().clear();
                }
            }

            AgentResult executorResult = runAgent(AgentRole.EXECUTOR, context);
            if (!executorResult.isSuccess()) {
                log.error("[Orchestrator] Executor 失败，终止 Pipeline");
                return executorResult;
            }

            runAgent(AgentRole.REVIEWER, context);
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
     * 执行单个 Agent（委托给 AgentDispatcher）
     */
    public AgentResult runAgent(AgentRole role, AgentContext context) {
        return agentDispatcher.dispatch(role, context);
    }
}
