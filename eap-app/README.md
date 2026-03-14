# eap-app

> 主应用模块，监听端口 **8081**，将所有层整合为单一可部署应用，提供全量 REST API。

## 职责

- 合并原来的多个微服务为单一 Spring Boot 应用
- 扫描并装配所有 `com.enterprise.agent.*` 包下的 Bean
- 提供 JWT 鉴权（可选，默认关闭）
- 暴露所有业务 REST API 和 Swagger 文档

## 包含内容

### 应用入口（`com.enterprise.agent.app`）

```java
@SpringBootApplication
@EnableAsync
@EnableFeignClients
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan("com.enterprise.agent.data.mapper")
public class EapApplication { ... }
```

### 配置类（`com.enterprise.agent.app.config`）

| 类 | 说明 |
|----|------|
| `SecurityConfig` | JWT 安全配置，由 `eap.security.enabled` 控制（默认 false） |
| `AsyncConfig` | 异步线程池配置，供 `AgentPipelineService` 使用 |

### 安全组件（`com.enterprise.agent.app.security`）

| 类 | 说明 |
|----|------|
| `JwtUtil` | JWT 生成/验证/用户名提取（默认有效期 24 小时） |
| `JwtAuthFilter` | Spring Security 过滤器，验证 Bearer Token 并设置 SecurityContext |

### 全量 API 接口汇总

| Controller | 路径前缀 | 说明 |
|-----------|---------|------|
| `AgentTaskController` | `/agent/tasks` | 任务 CRUD + 流水线触发 + 报告获取 |
| `AuditEngineController` | `/audit` | 数据同步 / 规则扫描 / 完整稽核 / 疑点查询 |
| `ProcurementAuditController` | `/procurement/audit` | AI 招采稽核（全场景/单场景） |
| `InteractionCenterController` | `/chat` | 多轮对话 + 会话历史 |
| `InsightController` | `/insight` | NL2BI 数据洞察 |
| `KnowledgeController` | `/knowledge` | 知识问答（RAG） |
| `EnterpriseToolController` | `/tools` | 工具直接调用 |

### 启动依赖

| 依赖 | 用途 |
|------|------|
| PostgreSQL | 主数据库（eap_db，端口 5432） |
| Redis | LLM 响应缓存（端口 6379） |
| Kafka | 任务事件流（broker 9092，topic: agent-task-events） |
| OpenAI / Anthropic API Key | LLM 调用（至少配置一个） |

## 配置说明

```yaml
# application.yml 关键配置
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/eap_db
    username: ${DB_USERNAME:eap_user}
    password: ${DB_PASSWORD:eap_pass}
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}

eap:
  llm:
    provider: openai        # openai | claude | ollama
    cache-enabled: true
    cache-ttl-seconds: 3600
  security:
    enabled: false          # 设为 true 开启 JWT 鉴权
    jwt:
      secret: ${JWT_SECRET}
      expiration-ms: 86400000
  audit:
    org-codes: ORG001
    interval-seconds: 3600
```

## 快速启动

```bash
# 1. 确保 PostgreSQL / Redis / Kafka 已启动
# 2. 设置环境变量
export OPENAI_API_KEY=sk-...
export DB_USERNAME=eap_user
export DB_PASSWORD=eap_pass

# 3. 启动应用
cd eap-app
mvn spring-boot:run

# 应用启动后：
# API 地址: http://localhost:8081
# Swagger UI: http://localhost:8081/swagger-ui.html
# Actuator:   http://localhost:8081/actuator/health
```

## API 快速参考

```bash
# 创建分析任务
curl -X POST http://localhost:8081/agent/tasks \
  -H "Content-Type: application/json" \
  -d '{"taskName":"ORG001审查","goal":"全面分析ORG001采购合规情况"}'

# 执行完整稽核
curl -X POST http://localhost:8081/audit/full \
  -H "Content-Type: application/json" \
  -d '{"orgCode":"ORG001"}'

# 多轮对话
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s001","message":"分析采购部今年的支出趋势"}'

# 知识问答
curl -X POST http://localhost:8081/knowledge/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"什么情况下需要进行政府采购？"}'
```

## 依赖关系

- 依赖所有其他模块：`eap-common`、`eap-data-*`、`eap-engine-*`、`eap-business-*`
- 通过 `eap-gateway`（:8080）对外暴露
