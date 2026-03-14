# eap-engine-llm

> LLM 服务模块，封装 Spring AI 多模型调用，并通过 Redis 缓存降低重复请求成本。

## 职责

- 实现 `LlmService` 和 `EmbeddingService` 接口（定义于 `eap-common`）
- 支持 OpenAI / Claude / Ollama 三种模型提供商，通过配置一键切换
- 基于 MD5 缓存键 + Redis 实现 LLM 响应缓存（TTL 1 小时）
- 支持多轮对话历史传递

## 包含内容

### 配置类（`com.enterprise.agent.llm.config`）

**`LlmProviderConfig`**（`@ConfigurationProperties(prefix="eap.llm")`）

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `provider` | `openai` | 当前激活的 LLM 提供商（openai / claude / ollama） |
| `cacheEnabled` | `true` | 是否开启 Redis 缓存 |
| `cacheTtlSeconds` | `3600` | 缓存 TTL（秒） |
| `timeoutSeconds` | `60` | LLM 调用超时（秒） |

**`RedisConfig`**

- `redisTemplate<String, Object>`：Jackson 序列化（含 JavaTimeModule）
- `cacheManager`：TTL 1 小时，禁用 null 值缓存
- 使用 `@EnableCaching` 注解启用缓存

### 服务类（`com.enterprise.agent.llm.service`）

**`SpringAiLlmService`**（实现 `LlmService`）

| 方法 | 说明 |
|------|------|
| `chat(LlmRequest)` | 全参数调用，支持系统提示词和对话历史 |
| `simpleChat(String)` | 简单单轮对话 |
| `chatWithSystem(String, String)` | 指定系统提示词的单轮对话 |
| `getProviderName()` | 返回当前提供商名称 |

- 缓存键：`MD5(provider + systemPrompt + userMessage)`
- 失败处理：抛出 `LlmException`

**`SpringAiEmbeddingService`**（实现 `EmbeddingService`）

- 使用 Spring AI `EmbeddingModel` 生成向量
- `embed(EmbeddingRequest)` → `EmbeddingResponse`（含 float[] 向量）

## 依赖关系

- 依赖：`eap-common`
- 外部依赖：`spring-ai-openai-spring-boot-starter`、`spring-ai-anthropic-spring-boot-starter`、`spring-ai-ollama-spring-boot-starter`、`spring-boot-starter-data-redis`
- 被依赖：`eap-engine-rag`、`eap-engine-agent-*`、`eap-business-*`

## 快速使用

### 切换 LLM 提供商

```yaml
# application.yml
eap:
  llm:
    provider: claude  # openai | claude | ollama

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat.options.model: claude-sonnet-4-6
    ollama:
      base-url: http://localhost:11434
      chat.options.model: llama3.1
```

### 代码调用

```java
@Autowired LlmService llmService;

// 简单调用（自动缓存）
String answer = llmService.simpleChat("请解释什么是招标违规");

// 带系统提示词
String answer = llmService.chatWithSystem(
    "你是一位招采稽核专家",
    "分析以下付款记录是否存在异常：..."
);

// 多轮对话
LlmRequest req = LlmRequest.builder()
    .systemPrompt("你是智能助手")
    .userMessage("继续上次的分析")
    .history(conversationHistory)
    .temperature(0.7)
    .build();
LlmResponse resp = llmService.chat(req);
```
