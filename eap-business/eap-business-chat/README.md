# eap-business-chat

> AI 交互中心模块，提供多轮对话、意图识别路由和自然语言数据洞察（NL2BI）能力。

## 职责

- 实现 `InteractionCenterAgent` 作为统一对话入口，识别意图并路由到对应能力
- 实现 `InsightAgent` 完成 NL2BI 三步流水线（自然语言 → SQL → 分析）
- 维护 `ConversationSession` 管理多会话历史（保留最近5轮）
- 暴露 Chat REST API

## 包含内容

### InteractionCenterAgent（`com.enterprise.agent.business.chat`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.INTERACTION_CENTER` |
| 核心能力 | LLM 意图分类 → 路由 → 多轮对话记忆 |

#### 意图路由逻辑

| 意图 | 触发条件（示例） | 路由目标 |
|------|----------------|---------|
| `PLANNING` | "帮我制定...计划"、"分析...任务" | `AgentOrchestrator.runPipeline()` |
| `KNOWLEDGE` | "什么是..."、"规定是..."、"政策..." | `KnowledgeQaService.answer()`（RAG） |
| `INSIGHT` | "各部门...数据"、"统计..."、"趋势" | `InsightAgent.investigate()`（NL2BI） |
| `GENERAL` | 其他闲聊/咨询 | 直接 LLM 响应（含对话历史） |

#### 主要方法

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | Task-oriented 接口，供 AgentOrchestrator 调用 |
| `chat(sessionId, userMessage)` | 交互式对话接口，返回 `InteractionResult` |

### InsightAgent（`com.enterprise.agent.business.chat`）

NL2BI 三步流水线：

```
用户问题
  ↓ Step 1: NlToSqlService.generateSql()
  SQL 语句
  ↓ Step 2: DataQueryService.executeQuery()
  原始数据（List<Map>）
  ↓ Step 3: InsightAnalysisService.analyze()
  InsightResult（分析文本 + chartHint）
```

| 字段 | 说明 |
|------|------|
| `question` | 原始问题 |
| `generatedSql` | 生成的 SQL |
| `rawData` | 查询原始数据 |
| `analysis` | LLM 分析文本 |
| `chartHint` | 图表类型建议（PIE/BAR/LINE 等） |

### ConversationSession（`com.enterprise.agent.business.chat`）

内存会话管理（按 sessionId 隔离）：

| 方法 | 说明 |
|------|------|
| `addMessage(sessionId, role, content)` | 追加消息到会话历史 |
| `getHistory(sessionId)` | 获取会话历史（最近5轮） |

### InteractionResult（`com.enterprise.agent.business.chat`）

| 字段 | 说明 |
|------|------|
| `sessionId` | 会话 ID |
| `userMessage` | 用户原始消息 |
| `agentType` | 处理该消息的 Agent 类型 |
| `response` | Agent 返回的响应 |
| `usedTools` | 本次调用的工具列表 |
| `timestamp` | 响应时间戳 |

### REST API（`com.enterprise.agent.business.chat.controller`）

#### InteractionCenterController

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/chat` | `{sessionId, message}` | 多轮对话（含意图识别和路由） |
| GET | `/chat/sessions/{sessionId}` | — | 获取指定会话的完整历史 |

## 依赖关系

- 依赖：`eap-common`、`eap-engine-agent-core`、`eap-engine-rag`、`eap-engine-llm`、`eap-business-screening`
- `eap-business-chat` → `eap-business-screening`：`InteractionCenterAgent` 调用 `ProcurementAuditAgent` 处理稽核类请求

## 快速使用

```bash
# 多轮对话（意图：知识问答）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"什么情况下采购需要强制公开招标？"}'

# 多轮对话（意图：数据洞察）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"展示各部门今年的采购金额分布"}'

# 多轮对话（意图：任务规划）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"帮我分析 ORG001 的采购合规风险"}'

# 查看会话历史
curl http://localhost:8081/chat/sessions/sess-001
```
