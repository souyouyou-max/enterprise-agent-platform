# aip-engine-agent-core

> Agent 框架核心模块：定义 Agent 抽象、上下文模型、调度器、编排器和跨模块能力接口。

## 模块职责

- 提供 `BaseAgent` 抽象基类与通用执行能力。
- 提供 `AgentContext`、`AgentResult` 统一模型。
- 提供 `AgentDispatcher` 与 `AgentOrchestrator`。
- 承载跨业务能力接口（如 `AuditingCapability`），用于模块解耦。

## 核心类

- `com.sinosig.aip.core.agent.BaseAgent`
- `com.sinosig.aip.core.context.AgentContext`
- `com.sinosig.aip.core.context.AgentResult`
- `com.sinosig.aip.core.dispatcher.AgentDispatcher`
- `com.sinosig.aip.core.orchestrator.AgentOrchestrator`
- `com.sinosig.aip.core.capability.AuditingCapability`

## 近期架构调整

- 已移除 `agent-core` 对 provider starter 的直接依赖。
- 当前通过 `aip-engine-llm` 提供 LLM 能力，降低 provider 耦合。

## 依赖关系

- 依赖：`aip-common`、`aip-engine-llm`
- 被依赖：`aip-business-*`、`aip-engine-*`

## 本地验证

```bash
mvn -pl aip-engine/aip-engine-agent-core -am -DskipTests compile
```
