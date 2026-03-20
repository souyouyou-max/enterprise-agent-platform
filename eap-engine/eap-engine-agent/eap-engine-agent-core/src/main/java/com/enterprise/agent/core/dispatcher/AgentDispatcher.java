package com.enterprise.agent.core.dispatcher;

import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
