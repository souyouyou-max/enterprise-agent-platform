# aip-business-chat

> AI 交互中心模块：统一对话入口、会话管理、工具编排和 NL2BI 洞察。

## 模块职责

- 提供 `/api/v1/chat` 对话 API（普通 + SSE 流式）。
- 通过 `InteractionCenterAgent` 统一编排对话请求。
- 通过 `AgentOrchestrationToolkit` 暴露工具能力给 LLM。
- 提供 `InsightAgent`（NL2BI）和会话生命周期管理。

## 关键设计

- 已移除 `chat -> auditing` 直接依赖。
- 当前通过 `AuditingCapability`（位于 `aip-engine-agent-core`）调用稽核能力，避免同层硬耦合。
- `pom.xml` 内置 Enforcer 规则，禁止再次直接依赖 `aip-business-auditing`。

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/chat` | 普通对话 |
| `POST` | `/api/v1/chat/stream` | SSE 流式对话 |
| `POST` | `/api/v1/chat/session` | 创建会话 |
| `DELETE` | `/api/v1/chat/session/{sessionId}` | 清空会话 |
| `GET` | `/api/v1/chat/session/{sessionId}/history` | 查询会话历史 |

## 核心类

- `InteractionCenterAgent`
- `AgentOrchestrationToolkit`
- `ConversationSession`
- `InteractionCenterController`

## 本地验证

```bash
# 仅编译本模块
mvn -pl aip-business/aip-business-chat -am -DskipTests compile

# 快速请求
curl -X POST http://localhost:8079/api/v1/chat/session
```
