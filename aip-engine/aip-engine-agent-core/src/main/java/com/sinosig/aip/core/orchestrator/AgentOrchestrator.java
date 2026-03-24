package com.sinosig.aip.core.orchestrator;

import com.sinosig.aip.common.core.enums.AgentRole;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import com.sinosig.aip.core.dispatcher.AgentDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AgentOrchestrator - Agent 编排器
 * <p>
 * 提供两种执行入口：
 * <ul>
 *   <li>{@link #runAgent} - 执行单个 Agent，委托给 AgentDispatcher。</li>
 *   <li>{@link #runPipeline} - 轻量级同步 Pipeline（无 DB 持久化），供 Chat 侧「全链路分析」等工具场景使用。</li>
 * </ul>
 * <p>
 * <b>Reviewer 失败说明：</b>ReviewerAgent 设计为"总是返回 success=true"，
 * 通过 {@code context.isReviewPassed()} 和 {@code context.getReviewScore()} 传递质量结论。
 * 因此本 Pipeline 中不再对 reviewerResult.isSuccess() == false 做终止处理，
 * 而是通过 context 的 reviewPassed 标志驱动重试逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentDispatcher agentDispatcher;

    /**
     * 执行单个 Agent（委托给 AgentDispatcher）
     */
    public AgentResult runAgent(AgentRole role, AgentContext context) {
        return agentDispatcher.dispatch(role, context);
    }

    /**
     * 轻量级同步 Pipeline：Planner → Executor → Reviewer（可重试）→ Communicator
     * <p>
     * 适用于不需要 DB 状态持久化的场景（如 Chat 工具调用）。
     *
     * @param context          任务上下文
     * @param maxReviewRetries Reviewer 质量不达标时，Executor 最多重试次数
     * @return 最终的 AgentResult（来自 Communicator 或最后失败步骤）
     */
    public AgentResult runPipeline(AgentContext context, int maxReviewRetries) {
        log.info("[Orchestrator] 开始同步 Pipeline, taskId={}", context.getTaskId());

        // Step 1: Planner
        AgentResult plannerResult = runAgent(AgentRole.PLANNER, context);
        if (!plannerResult.isSuccess()) {
            log.error("[Orchestrator] Planner 失败，终止 Pipeline");
            return plannerResult;
        }

        // Step 2: Executor + Reviewer（支持重试）
        // ReviewerAgent 始终返回 success=true，质量通过与否由 context.isReviewPassed() 决定。
        int reviewAttempt = 0;
        do {
            if (reviewAttempt > 0) {
                log.warn("[Orchestrator] 质量不达标（评分={}/100），第 {} 次重新执行 Executor",
                        context.getReviewScore(), reviewAttempt);
                if (context.getReviewIssues() != null) {
                    context.getReviewIssues().clear();
                }
            }

            AgentResult executorResult = runAgent(AgentRole.EXECUTOR, context);
            if (!executorResult.isSuccess()) {
                log.error("[Orchestrator] Executor 失败，终止 Pipeline");
                return executorResult;
            }

            // Reviewer 不再对 isSuccess()==false 做 fatal 处理，
            // 异常情况下 ReviewerAgent 会给零分并设置 reviewPassed=false，由重试循环接管。
            runAgent(AgentRole.REVIEWER, context);
            reviewAttempt++;

        } while (!context.isReviewPassed() && reviewAttempt < maxReviewRetries);

        if (!context.isReviewPassed()) {
            log.warn("[Orchestrator] 达到最大重试次数 {}，评分={}，终止并报告", maxReviewRetries, context.getReviewScore());
        }

        // Step 3: Communicator
        AgentResult communicatorResult = runAgent(AgentRole.COMMUNICATOR, context);
        log.info("[Orchestrator] 同步 Pipeline 完成, taskId={}, 最终评分={}",
                context.getTaskId(), context.getReviewScore());
        return communicatorResult;
    }
}
