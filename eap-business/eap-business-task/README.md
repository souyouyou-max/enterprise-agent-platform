# eap-business-task

> 任务管理业务模块，提供 Agent 任务的完整生命周期管理，通过 Kafka 事件追踪状态流转。

## 职责

- 接收用户任务请求，驱动 Planner → Executor → Reviewer → Communicator 四 Agent 流水线
- 管理任务状态流转，持久化各阶段结果到数据库
- 发布 Kafka 事件实现异步通知和状态追踪
- 提供任务查询、重试和报告获取接口

## 包含内容

### Agent 实现

#### PlannerAgent（`com.enterprise.agent.business.task.planner`）

| 属性 | 值 |
|------|----|
| 角色 | `AgentRole.PLANNER` |
| 输出 | 3-5 个 JSON 格式子任务（sequence / description / toolName / toolParams） |
| 可用工具 | `getSalesData` / `getEmployeeInfo` / `queryCrmData` / `generateSqlQuery` / `none` |
| 容错 | JSON 解析失败时生成默认子任务 |

#### ExecutorAgent（`com.enterprise.agent.business.task.executor`）

| 属性 | 值 |
|------|----|
| 角色 | `AgentRole.EXECUTOR` |
| 流程 | 顺序执行子任务：调用工具 → LLM 分析结果 → 存储到 executionResults |
| 工具重试 | MAX_TOOL_RETRIES = 3，指数退避（500ms × attempt） |

#### ReviewerAgent（`com.enterprise.agent.business.task.reviewer`）

| 属性 | 值 |
|------|----|
| 角色 | `AgentRole.REVIEWER` |
| 评分维度 | 完整性（30%）/ 准确性（30%）/ 可用性（20%）/ 合规性（20%） |
| 质量阈值 | 60分，低于阈值触发 Executor 重新执行 |
| 输出 | JSON（score / passed / issues / summary） |

### 任务生命周期

```
PENDING
  ↓（任务创建 → 发布 CREATED 事件）
PLANNING
  ↓（PlannerAgent 分解子任务）
EXECUTING
  ↓（ExecutorAgent 执行工具调用）
REVIEWING
  ↓（ReviewerAgent 质量评分）
  ├─ score ≥ 60 → COMMUNICATING
  │     ↓（CommunicatorAgent 生成报告）
  │     ↓
  │  COMPLETED
  └─ score < 60 → 重新 EXECUTING（最多2次重试）
             ↓（超重试次数）
           FAILED
```

### AgentTaskService（`com.enterprise.agent.business.task.service`）

| 方法 | 说明 |
|------|------|
| `createAndStartTask(taskName, goal)` | 创建任务 + 发布 CREATED 事件 + 异步执行流水线 |
| `getTask(taskId)` | 查询任务详情 |
| `listTasks(page, size)` | 分页查询任务列表 |
| `retryTask(taskId)` | 重置 FAILED 任务为 PENDING 并重新执行 |
| `getReport(taskId)` | 获取最终报告（任务需为 COMPLETED 状态） |

### AgentPipelineService（`com.enterprise.agent.business.task.service`）

- `executeAsync(taskId, taskName, goal)`：`@Async` 异步执行，流水线在后台线程池运行

### Kafka 事件（`com.enterprise.agent.business.task.event`）

**Topic**：`agent-task-events`

**AgentTaskEvent 字段**：

| 字段 | 说明 |
|------|------|
| `taskId` / `taskName` / `goal` | 任务标识信息 |
| `previousStatus` / `currentStatus` | 状态流转 |
| `reviewScore` | 审查评分 |
| `eventType` | CREATED / STATUS_CHANGED / COMPLETED / FAILED |
| `message` | 状态说明 |
| `occurredAt` | 事件时间戳 |

### REST API（`com.enterprise.agent.business.task.controller`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/tasks` | 创建并启动任务 |
| GET | `/agent/tasks/{id}` | 查询任务详情 |
| GET | `/agent/tasks` | 分页查询任务列表 |
| GET | `/agent/tasks/{id}/report` | 获取最终报告 |
| POST | `/agent/tasks/{id}/retry` | 重试失败任务 |

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`、`eap-engine-agent-core`、`eap-engine-llm`
- 外部依赖：`spring-kafka`

## 快速使用

```bash
# 创建任务
curl -X POST http://localhost:8081/agent/tasks \
  -H "Content-Type: application/json" \
  -d '{"taskName":"采购合规审查","goal":"分析 ORG001 近三个月的采购行为是否合规"}'

# 查询任务状态
curl http://localhost:8081/agent/tasks/1

# 获取最终报告（任务完成后）
curl http://localhost:8081/agent/tasks/1/report
```
