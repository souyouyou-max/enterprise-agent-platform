# eap-engine-agent-core

> Agent 核心抽象模块，定义 Agent 框架的基础类、上下文模型、编排器和分发器。

## 职责

- 提供 `BaseAgent` 抽象基类，统一所有 Agent 的生命周期
- 定义 `AgentContext` 和 `AgentResult` 数据模型
- 实现 `AgentOrchestrator` 编排 Planner→Executor→Reviewer→Communicator 流水线
- 实现 `AgentDispatcher` 按角色动态路由并执行 Agent

## 包含内容

### BaseAgent（`com.enterprise.agent.core.agent`）

```java
public abstract class BaseAgent {
    // 必须实现的抽象方法
    protected abstract AgentRole getRole();          // Agent 角色标识
    protected abstract String getSystemPrompt();     // 系统提示词
    protected abstract AgentResult execute(AgentContext context);

    // 工具方法
    protected ChatClient buildChatClient()                            // 构建注入系统提示词的 ChatClient
    protected String callLlmWithRetry(String prompt, int maxRetries)  // 指数退避重试（1000ms * attempt）
    protected String sanitizeInput(String input)                      // 过滤 </system>/<system> 等注入标记
    protected void logStart(AgentContext context)
    protected void logEnd(AgentContext context, AgentResult result)
}
```

### AgentContext（`com.enterprise.agent.core.context`）

流水线各阶段共享的上下文对象（`@Data @Builder`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | Long | 关联任务 ID |
| `goal` | String | 用户目标描述 |
| `taskName` | String | 任务名称 |
| `reportStyle` | ReportStyle | 报告风格（EMAIL/SUMMARY/DETAILED） |
| `metadata` | Map | 扩展元数据 |
| `subTasks` | `List<SubTask>` | Planner 输出的子任务列表 |
| `executionResults` | `Map<Integer, String>` | Executor 输出（序号→结果） |
| `reviewScore` | Integer | Reviewer 评分（0-100） |
| `reviewIssues` | `List<String>` | Reviewer 发现的问题 |
| `reviewPassed` | boolean | 是否通过质量阈值（≥60分） |
| `finalReport` | String | Communicator 生成的最终报告 |
| `conversationHistory` | `List<String>` | 多轮对话历史 |

**嵌套类 `SubTask`**：sequence / description / toolName / toolParams / result / status（PENDING/EXECUTING/COMPLETED/FAILED）

### AgentResult（`com.enterprise.agent.core.context`）

Agent 执行结果（`@Data @Builder`）：

| 字段 | 说明 |
|------|------|
| `agentRole` | 执行的 Agent 角色 |
| `output` | 输出文本 |
| `success` | 是否成功 |
| `errorMessage` | 错误信息 |
| `subTaskResults` | Executor 专用：子任务结果 Map |
| `qualityScore` | Reviewer 专用：质量评分 |
| `issues` | Reviewer 专用：问题列表 |
| `executedAt` | 执行时间戳 |
| `retryCount` | 重试次数 |

工厂方法：`AgentResult.failure(role, errorMessage)`

### AgentOrchestrator（`com.enterprise.agent.core.orchestrator`）

编排 Agent 流水线执行：

```java
// 完整流水线（带审查重试）
AgentResult result = orchestrator.runPipeline(context, maxReviewRetries);
// 流程：Planner → Executor → Reviewer(循环) → Communicator
// 若 reviewPassed=false 且未超重试次数 → 重新执行 Executor

// 单 Agent 执行
AgentResult result = orchestrator.runAgent(AgentRole.PLANNER, context);
```

### AgentDispatcher（`com.enterprise.agent.core.dispatcher`）

按 `AgentRole` 动态路由 Agent：

```java
// 路由并执行
AgentResult result = dispatcher.dispatch(AgentRole.PLANNER, context);

// 端到端流水线（含重试，max=2）
AgentResult result = dispatcher.runPipeline(goal);

// 查看注册的 Agent 列表
List<AgentRole> roles = dispatcher.listRegisteredRoles();
```

- 使用 `@Lazy @Autowired List<BaseAgent>` 打破循环依赖
- 自动发现所有注册为 `@Component` 的 `BaseAgent` 子类

## 依赖关系

- 依赖：`eap-common`、`eap-engine-llm`
- 被依赖：`eap-engine-agent-clue`、`eap-engine-agent-risk`、`eap-engine-agent-monitor`、`eap-business-*`

## 快速使用

```java
@Autowired AgentDispatcher dispatcher;

// 完整 Agent 流水线
AgentResult result = dispatcher.runPipeline("分析 ORG001 的采购合规风险");

// 构建上下文并直接运行流水线
AgentContext ctx = AgentContext.builder()
    .taskId(1L)
    .goal("分析 ORG001 采购异常")
    .reportStyle(ReportStyle.SUMMARY)
    .build();
AgentOrchestrator orchestrator = ...;
AgentResult result = orchestrator.runPipeline(ctx, 2);
```
