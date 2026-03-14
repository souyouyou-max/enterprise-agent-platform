# eap-common

> 公共基础层，提供全项目共享的枚举、异常、响应封装和 AI 服务接口定义。

## 职责

- 定义统一 API 响应结构（`ResponseResult`、`PageResult`）
- 定义全局枚举（任务状态、报告风格、Agent 角色）
- 定义业务异常体系（`AgentException`、`LlmException`、`ToolExecutionException`）
- 声明 AI 服务接口（`LlmService`、`EmbeddingService`）及其请求/响应模型
- 提供全局异常处理器（`GlobalExceptionHandler`）

## 包含内容

### `com.enterprise.agent.common.core.enums`

| 枚举 | 值 |
|------|----|
| `TaskStatus` | PENDING / PLANNING / EXECUTING / REVIEWING / COMMUNICATING / COMPLETED / FAILED / RETRYING |
| `ReportStyle` | EMAIL / SUMMARY / DETAILED |
| `AgentRole` | PLANNER / EXECUTOR / REVIEWER / COMMUNICATOR / INTERACTION_CENTER / PROCUREMENT_AUDITOR / CLUE_DISCOVERY / RISK_ANALYSIS / MONITORING |

### `com.enterprise.agent.common.core.response`

| 类 | 说明 |
|----|------|
| `ResponseResult<T>` | 统一响应包装，含 code / message / data / timestamp；工厂方法 `success()` / `error()` |
| `PageResult<T>` | 分页响应，含 total / page / size / records；工厂方法 `of()` |

### `com.enterprise.agent.common.core.exception`

| 类 | 说明 |
|----|------|
| `AgentException` | 基础业务异常（code: 500） |
| `LlmException` | LLM 调用异常（code: 503） |
| `ToolExecutionException` | 工具执行异常，含 toolName 字段 |

### `com.enterprise.agent.common.ai.model`

| 类 | 说明 |
|----|------|
| `LlmRequest` | LLM 请求模型：userMessage / systemPrompt / history / temperature / maxTokens |
| `LlmResponse` | LLM 响应：content / model / cached / finishReason |
| `EmbeddingRequest` | 向量化请求：text / model |
| `EmbeddingResponse` | 向量化响应：embedding (float[]) / model / usage |

### `com.enterprise.agent.common.ai.service`

| 接口 | 主要方法 |
|------|---------|
| `LlmService` | `chat(LlmRequest)` / `simpleChat(String)` / `chatWithSystem(String, String)` / `getProviderName()` |
| `EmbeddingService` | `embed(EmbeddingRequest)` |

### `com.enterprise.agent.common.exception`

| 类 | 说明 |
|----|------|
| `GlobalExceptionHandler` | `@ControllerAdvice` 统一异常处理，映射各异常类型到 HTTP 状态码 |

## 依赖关系

- 无内部模块依赖（基础层，被所有其他模块依赖）
- 外部依赖：`spring-boot-starter-web`、`spring-ai-core`

## 快速使用

```java
// 统一响应
return ResponseResult.success(data);
return ResponseResult.error("操作失败");

// 抛出业务异常
throw new AgentException("任务不存在");
throw new LlmException("LLM 调用超时");

// 调用 LLM（注入接口即可切换实现）
@Autowired LlmService llmService;
String reply = llmService.simpleChat("你好");
```
