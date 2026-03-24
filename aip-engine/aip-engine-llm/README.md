# aip-engine-llm

> LLM 能力封装模块：对外提供 `LlmService` / `EmbeddingService` 抽象实现。

## 模块职责

- 实现 `aip-common` 中定义的 AI 服务接口。
- 承接 provider 相关依赖，避免业务层直接绑定具体模型 starter。
- 提供基础重试与缓存能力（按应用配置启用）。

## 关键类

- `SpringAiLlmService`
- `SpringAiEmbeddingService`
- `ChatModelConfig`

## 依赖关系

- 依赖：`aip-common`
- 被依赖：`aip-engine-agent-core`、`aip-engine-rag`、`aip-business-*`

## 使用建议

- provider 配置与 key 统一放在应用层配置中。
- 业务模块仅注入 `LlmService`，不要直接注入 provider starter Bean。
