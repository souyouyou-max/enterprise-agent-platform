package com.enterprise.agent.core.dispatcher;

import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.common.core.enums.ReportStyle;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AgentDispatcher - Agent 调度器
 * <p>
 * 根据 AgentRole 动态分发到对应 Agent，并提供完整 Pipeline 的便捷入口。
 * <p>
 * 注意：agents 列表使用 @Lazy 注入，以打破
 * InteractionCenterAgent → AgentOrchestrator → AgentDispatcher → List<BaseAgent>
 * 的构造器循环依赖链。
 */
@Slf4j
@Service
public class AgentDispatcher {

    /**
     * @Lazy 延迟注入：打破循环依赖。
     * AgentDispatcher 构造完成后不依赖任何 Agent Bean，
     * 首次 dispatch() 调用时才真正解析 agents 列表。
     */
    @Lazy
    @Autowired
    private List<BaseAgent> agents;

    /** 角色 → Agent 的映射，懒初始化 */
    private volatile Map<AgentRole, BaseAgent> agentRegistry;

    // ─────────────────────────────────────────────────────────────────────────
    // 核心 API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 根据角色分发到对应 Agent 执行
     */
    public AgentResult dispatch(AgentRole role, AgentContext context) {
        BaseAgent agent = getRegistry().get(role);
        if (agent == null) {
            String msg = "AgentDispatcher: 未找到 Agent for role=" + role;
            log.error(msg);
            return AgentResult.failure(role, msg);
        }
        try {
            log.info("[Dispatcher] dispatch → {}", role);
            return agent.execute(context);
        } catch (Exception e) {
            log.error("[Dispatcher] Agent {} 执行异常: {}", role, e.getMessage(), e);
            return AgentResult.failure(role, e.getMessage());
        }
    }

    /**
     * 执行完整流水线：Planner → Executor → Reviewer（最多2次重试）→ Communicator
     *
     * @param goal 用户目标描述
     * @return Communicator 最终输出结果
     */
    public AgentResult runPipeline(String goal) {
        log.info("[Dispatcher] runPipeline, goal={}", goal.substring(0, Math.min(50, goal.length())));

        AgentContext context = AgentContext.builder()
                .taskId(System.currentTimeMillis())
                .taskName("Dispatcher-Pipeline")
                .goal(goal)
                .reportStyle(ReportStyle.DETAILED)
                .reviewIssues(new ArrayList<>())
                .executionResults(new HashMap<>())
                .build();

        // Step 1: Planner
        AgentResult plannerResult = dispatch(AgentRole.PLANNER, context);
        if (!plannerResult.isSuccess()) {
            log.error("[Dispatcher] Planner 失败，终止 Pipeline");
            return plannerResult;
        }

        // Step 2: Executor + Reviewer，最多重试 2 次
        int maxRetries = 2;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (attempt > 0) {
                log.warn("[Dispatcher] 质量未达标，第 {} 次重试 Executor", attempt);
                if (context.getReviewIssues() != null) {
                    context.getReviewIssues().clear();
                }
            }
            AgentResult executorResult = dispatch(AgentRole.EXECUTOR, context);
            if (!executorResult.isSuccess()) {
                log.error("[Dispatcher] Executor 失败，终止 Pipeline");
                return executorResult;
            }
            dispatch(AgentRole.REVIEWER, context);
            if (context.isReviewPassed()) {
                break;
            }
        }

        // Step 3: Communicator
        AgentResult finalResult = dispatch(AgentRole.COMMUNICATOR, context);
        log.info("[Dispatcher] Pipeline 完成, reviewScore={}", context.getReviewScore());
        return finalResult;
    }

    /**
     * 列出已注册的所有 Agent 角色
     */
    public List<AgentRole> listRegisteredRoles() {
        return new ArrayList<>(getRegistry().keySet());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私有方法
    // ─────────────────────────────────────────────────────────────────────────

    private Map<AgentRole, BaseAgent> getRegistry() {
        if (agentRegistry == null) {
            synchronized (this) {
                if (agentRegistry == null) {
                    agentRegistry = agents.stream()
                            .collect(Collectors.toMap(
                                    BaseAgent::getRole,
                                    Function.identity(),
                                    (a, b) -> {
                                        log.warn("[Dispatcher] 重复 Agent role={}, 保留第一个", a.getRole());
                                        return a;
                                    }
                            ));
                    log.info("[Dispatcher] 注册 {} 个 Agent: {}",
                            agentRegistry.size(),
                            agentRegistry.keySet().stream()
                                    .map(AgentRole::name)
                                    .collect(Collectors.joining(", ")));
                }
            }
        }
        return agentRegistry;
    }
}
