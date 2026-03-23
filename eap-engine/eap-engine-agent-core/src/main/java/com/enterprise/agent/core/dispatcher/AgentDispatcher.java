package com.enterprise.agent.core.dispatcher;

import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import jakarta.annotation.PostConstruct;
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
 * 循环依赖说明：
 * InteractionCenterAgent → AgentOrchestrator → AgentDispatcher → List&lt;BaseAgent&gt;
 * 通过对 agents 字段使用 {@code @Lazy} 延迟注入来打破该链路。
 * agentRegistry 在 {@code @PostConstruct} 中一次性构建，避免了运行时 DCL（双重检查锁）。
 */
@Slf4j
@Service
public class AgentDispatcher {

    /**
     * @Lazy 延迟注入：打破构造阶段的循环依赖。
     * Spring 容器完成所有 Bean 初始化后，@PostConstruct 触发时再解析此列表。
     */
    @Lazy
    @Autowired
    private List<BaseAgent> agents;

    /** 角色 → Agent 的只读映射，由 @PostConstruct 初始化，之后不再修改 */
    private Map<AgentRole, BaseAgent> agentRegistry;

    // ─────────────────────────────────────────────────────────────────────────
    // 初始化
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 在所有 Bean 注入完成后构建 agentRegistry。
     * 使用 @PostConstruct 替代运行时 DCL，代码更清晰，也更利于测试。
     */
    @PostConstruct
    void buildRegistry() {
        agentRegistry = agents.stream()
                .collect(Collectors.toMap(
                        BaseAgent::getRole,
                        Function.identity(),
                        (a, b) -> {
                            log.warn("[Dispatcher] 重复 Agent role={}，保留先注册的: {}",
                                    a.getRole(), a.getClass().getSimpleName());
                            return a;
                        }
                ));
        log.info("[Dispatcher] 注册 {} 个 Agent: {}",
                agentRegistry.size(),
                agentRegistry.keySet().stream()
                        .map(AgentRole::name)
                        .collect(Collectors.joining(", ")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 核心 API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 根据角色分发到对应 Agent 执行
     */
    public AgentResult dispatch(AgentRole role, AgentContext context) {
        BaseAgent agent = agentRegistry.get(role);
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
        return new ArrayList<>(agentRegistry.keySet());
    }
}
