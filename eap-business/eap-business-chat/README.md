# eap-business-chat

> AI 交互中心模块，提供多轮对话和 LLM 驱动的 Agent 自动编排能力。

## 职责

- 实现 `InteractionCenterAgent` 作为统一对话入口，将业务 Agent 作为工具注册给 LLM，由 LLM 自主决策调用哪些 Agent 及顺序
- 实现 `AgentOrchestrationToolkit` 将四个业务 Agent 封装为可供 LLM 调用的 `@Tool` 方法
- 实现 `InsightAgent` 完成 NL2BI 三步流水线（自然语言 → SQL → 分析）
- 维护 `ConversationSession` 管理多会话历史（保留最近 10 轮）
- 暴露 Chat REST API

## 编排模式：LLM 自动决策工具调用

### 核心思路

传统路由模式需要先做意图分类（PLANNING/KNOWLEDGE/INSIGHT/GENERAL），再 switch-case 路由。
新编排模式直接将四个业务 Agent 注册为 `@Tool`，由 LLM 根据用户消息自主决定：
- 调用哪些 Agent（可以是一个或多个）
- 调用顺序（如先线索发现，再风险透视）
- 是否需要追问用户（如缺少 orgCode 时主动询问）

```
用户输入
  ↓
InteractionCenterAgent（ChatClient + AgentOrchestrationToolkit）
  ↓ LLM 自主决策
┌──────────────────────────────────────────────────────┐
│  discoverClues  analyzeRisk  checkMonitoring  auditProcurement  │
└──────────────────────────────────────────────────────┘
  ↓ AgentContext → execute() → AgentResult
  ↓ LLM 汇总
综合分析报告（中文）
```

## 包含内容

### AgentOrchestrationToolkit（`com.enterprise.agent.business.chat.toolkit`）

将四个业务 Agent 封装为 LLM 可调用的 `@Tool` 方法：

| 工具方法 | 对应 Agent | Tool 描述 |
|---------|-----------|---------|
| `discoverClues(orgCode)` | `ClueDiscoveryAgent` | 线索发现：扫描采购/财务/合同数据，发现异常疑点线索 |
| `analyzeRisk(orgCode)` | `RiskAnalysisAgent` | 风险透视：多维风险评分和综合分析报告 |
| `checkMonitoring(orgCode)` | `MonitoringAgent` | 监测预警：检查风险指标阈值，返回预警列表和建议 |
| `auditProcurement(orgCode)` | `ProcurementAuditAgent` | 招采稽核：检测未招标/化整为零/围标串标/利益输送四类违规 |

每个工具通过 `AgentContext.builder()` 构造目标描述，统一走 `BaseAgent.execute()` 链路，与独立 Agent 接口保持一致。

### InteractionCenterAgent（`com.enterprise.agent.business.chat`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.INTERACTION_CENTER` |
| 核心能力 | ChatClient + AgentOrchestrationToolkit → LLM 自动工具调用编排 |

#### 主要方法

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | Task-oriented 接口，供 AgentOrchestrator 调用 |
| `chat(sessionId, userMessage)` | 交互式对话接口，返回 `InteractionResult` |

#### System Prompt 工作原则

1. 根据用户需求，自主决定调用哪些工具、调用顺序
2. 用户要"全面分析"时，依次调用全部工具并整合报告
3. 用户只问某个方面时，只调用对应工具
4. 调用完工具后，用清晰的中文汇总分析结论
5. 如果用户没提供机构编码，询问后再执行

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

### ConversationSession（`com.enterprise.agent.business.chat`）

内存会话管理（按 sessionId 隔离，滑动窗口 20 条消息）：

| 方法 | 说明 |
|------|------|
| `addMessage(sessionId, role, content)` | 追加消息到会话历史 |
| `getHistory(sessionId)` | 获取会话历史（List<Message>） |

### REST API（`com.enterprise.agent.business.chat.controller`）

#### InteractionCenterController

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/chat` | `{sessionId, message}` | 多轮对话（LLM 自动工具编排） |
| GET | `/chat/sessions/{sessionId}` | — | 获取指定会话的完整历史 |

## 依赖关系

- 依赖：`eap-common`、`eap-engine-agent-core`、`eap-engine-agent-clue`、`eap-engine-agent-risk`、`eap-engine-agent-monitor`、`eap-engine-rag`、`eap-engine-llm`、`eap-business-screening`
- `AgentOrchestrationToolkit` 依赖：`ClueDiscoveryAgent`、`RiskAnalysisAgent`、`MonitoringAgent`、`ProcurementAuditAgent`

## 快速使用

```bash
# 全面分析（LLM 自动调用四个工具）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"请对机构 ORG001 进行全面分析"}'

# 只做招采稽核（LLM 只调用 auditProcurement 工具）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"ORG001 有没有招采违规？"}'

# 风险和预警（LLM 调用 analyzeRisk + checkMonitoring）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"ORG002 的综合风险和预警情况如何？"}'

# 缺少 orgCode 时，LLM 会主动追问
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"sess-001","message":"帮我做个线索发现"}'

# 查看会话历史
curl http://localhost:8081/chat/sessions/sess-001
```
