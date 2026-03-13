# Enterprise Agent Platform (EAP)

> 企业级 Agent 系统 | 四层架构 | 多 Agent 协作 | Spring AI | Java 17+

[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M3-blue)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

## 项目简介

Enterprise Agent Platform (EAP) 是一个基于华为云企业级 Agent 部署架构设计的生产就绪型多 Agent 系统。系统采用**四层架构**，通过多个专职 Agent（规划、执行、审查、通信）协作完成复杂的企业业务分析任务。

**核心能力：**
- 自然语言目标 → 自动任务规划 → 工具调用执行 → 质量审查 → 报告生成
- 支持 OpenAI / Claude / Ollama 三种 LLM 后端热切换
- Redis 缓存 LLM 响应（TTL 1小时），降低成本
- Kafka 事件驱动的任务状态流转
- Spring Security + JWT 认证
- Actuator + Prometheus 可观测性

---

## 架构图

### 四层架构

```
┌──────────────────────────────────────────────────────────────┐
│                   User Interface Layer                        │
│            REST API（Spring Boot 3 + SpringDoc）               │
│   POST /api/v1/tasks  GET /api/v1/tasks/{id}/report  ...     │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                   Agent Core Layer                            │
│                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────┐│
│  │PlannerAgent │→ │ExecutorAgent │→ │ReviewerAgent │→ │Comm││
│  │目标→子任务   │  │子任务→工具调用│  │质量评分0-100  │  │报告││
│  └─────────────┘  └──────────────┘  └──────────────┘  └────┘│
│                                                              │
│              AgentOrchestrator（Pipeline 编排）                │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                   LLM Backend Layer                           │
│         Spring AI（OpenAI / Claude / Ollama 热切换）           │
│              Redis 缓存（TTL 1h）防重复调用                      │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│               Enterprise Integration Layer                    │
│  PostgreSQL（任务持久化）  Redis（LLM缓存）  Kafka（事件流）       │
│  SalesDataTool  EmployeeTool  CrmTool  SqlGeneratorTool      │
└──────────────────────────────────────────────────────────────┘
```

### 项目结构

```
enterprise-agent-platform/
├── pom.xml                              # 父 POM（依赖管理）
├── eap-common/
│   ├── eap-common-core/                 # 枚举/异常/统一响应
│   └── eap-common-ai/                   # LlmService/EmbeddingService 接口
├── eap-agent/
│   ├── eap-agent-core/                  # BaseAgent/AgentContext/AgentResult/Orchestrator
│   ├── eap-agent-planner/               # PlannerAgent：目标→子任务分解
│   ├── eap-agent-executor/              # ExecutorAgent：工具调用+重试
│   ├── eap-agent-reviewer/              # ReviewerAgent：质量评分
│   └── eap-agent-communicator/          # CommunicatorAgent：报告生成
├── eap-llm/
│   └── eap-llm-service/                 # Spring AI 实现+Redis缓存
├── eap-enterprise/
│   ├── eap-enterprise-tools/            # 企业工具（SalesData/Employee/CRM/SQL）
│   └── eap-enterprise-data/             # PostgreSQL实体+MyBatis-Plus
├── eap-task/                            # Pipeline编排+Kafka事件
└── eap-app/                             # Spring Boot 主应用（端口8080）
    ├── controller/                      # REST API
    ├── config/                          # Security/Async/OpenAPI配置
    ├── security/                        # JWT工具+过滤器
    └── resources/
        ├── application.yml
        └── db/schema.sql
```

---

## 多 Agent 协作流程

```
用户请求
    │
    ▼
POST /api/v1/tasks
    │  {goal: "分析本季度华南区销售情况"}
    │
    ▼
AgentTaskService.createAndStartTask()
    │
    ├─── [持久化] agent_task INSERT (status=PENDING)
    ├─── [Kafka]  发布 CREATED 事件
    └─── [Async]  触发 Pipeline

                    ┌──────────────────────────────────┐
                    │       异步 Pipeline 执行           │
                    │                                  │
                    │  1. PlannerAgent                 │
                    │     输入: goal                   │
                    │     输出: [子任务1, 子任务2, ...]  │
                    │     → status: PLANNING           │
                    │                                  │
                    │  2. ExecutorAgent                │
                    │     输入: subTasks               │
                    │     工具调用: getSalesData()      │
                    │     重试机制: 最多3次              │
                    │     → status: EXECUTING          │
                    │                                  │
                    │  3. ReviewerAgent                │
                    │     评分 < 60 → 重新执行Executor  │
                    │     评分 ≥ 60 → 通过              │
                    │     → status: REVIEWING          │
                    │                                  │
                    │  4. CommunicatorAgent            │
                    │     生成Markdown报告              │
                    │     支持EMAIL/SUMMARY/DETAILED   │
                    │     → status: COMPLETED          │
                    └──────────────────────────────────┘

GET /api/v1/tasks/{id}/report   ← 获取最终报告
```

---

## 快速开始

### 环境要求

| 依赖 | 版本要求 |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| PostgreSQL | 14+ |
| Redis | 6+ |
| Kafka | 3.x（可选，禁用则注释配置） |

### 1. 数据库初始化

```bash
# 创建数据库
psql -U postgres -c "CREATE USER eap_user WITH PASSWORD 'eap_password';"
psql -U postgres -c "CREATE DATABASE eap_db OWNER eap_user;"

# 初始化表结构
psql -U eap_user -d eap_db -f eap-app/src/main/resources/db/schema.sql
```

### 2. 配置环境变量

```bash
# 必填：选择 LLM Provider
export LLM_PROVIDER=openai           # openai | claude | ollama
export OPENAI_API_KEY=sk-xxx         # OpenAI API Key
# 或
export ANTHROPIC_API_KEY=sk-ant-xxx  # Claude API Key
# 或（Ollama 本地无需 key）
export LLM_PROVIDER=ollama
export OLLAMA_BASE_URL=http://localhost:11434

# 数据库（与 application.yml 默认值一致可省略）
export DB_USERNAME=eap_user
export DB_PASSWORD=eap_password

# Redis（默认 localhost:6379）
# export REDIS_HOST=localhost
# export REDIS_PORT=6379
```

### 3. 编译并启动

```bash
# 编译所有模块
mvn clean package -DskipTests

# 启动主应用
java -jar eap-app/target/eap-app-1.0.0-SNAPSHOT.jar

# 或使用 Maven
cd eap-app && mvn spring-boot:run
```

### 4. 验证启动

```
# Swagger UI
http://localhost:8080/swagger-ui.html

# Actuator 健康检查
http://localhost:8080/actuator/health

# Prometheus 指标
http://localhost:8080/actuator/prometheus
```

---

## API 接口文档

### 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/tasks` | 创建并启动 Agent 任务 |
| GET | `/api/v1/tasks/{id}` | 查询任务状态和详情 |
| GET | `/api/v1/tasks` | 分页查询任务列表 |
| POST | `/api/v1/tasks/{id}/retry` | 重试失败任务 |
| GET | `/api/v1/tasks/{id}/report` | 获取最终 Markdown 报告 |

#### 创建任务示例

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "华南区Q4销售分析",
    "goal": "分析2024年第四季度华南区销售情况，识别增长机会和风险点，生成改进建议"
  }'
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "taskName": "华南区Q4销售分析",
    "goal": "分析2024年第四季度华南区销售情况...",
    "status": "PENDING",
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

#### 查询任务状态

```bash
curl http://localhost:8080/api/v1/tasks/1
```

#### 获取分析报告

```bash
curl http://localhost:8080/api/v1/tasks/1/report
```

### 企业工具 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/enterprise/sales/{dept}` | 查询部门销售数据 |
| GET | `/api/v1/enterprise/employees/{id}` | 查询员工信息 |
| GET | `/api/v1/enterprise/crm/{customerId}` | 查询 CRM 客户数据 |
| GET | `/api/v1/enterprise/tools` | 查看已注册工具列表 |

```bash
# 查询华南区销售数据
curl "http://localhost:8080/api/v1/enterprise/sales/华南区?quarter=Q4-2024"

# 查询员工信息
curl http://localhost:8080/api/v1/enterprise/employees/E001

# 查询 CRM 数据
curl http://localhost:8080/api/v1/enterprise/crm/C001
```

---

## 配置说明

### LLM 切换方式

在 `application.yml` 或环境变量中修改 `eap.llm.provider`：

```yaml
eap:
  llm:
    provider: openai   # ← 修改此处：openai | claude | ollama
```

| Provider | 环境变量 | 默认模型 |
|----------|---------|---------|
| openai | `OPENAI_API_KEY` | gpt-4o |
| claude | `ANTHROPIC_API_KEY` | claude-sonnet-4-6 |
| ollama | `OLLAMA_BASE_URL` | llama3.1 |

### 安全配置

```yaml
eap:
  security:
    enabled: false   # 开发模式（false）/ 生产模式（true）
```

生产模式开启后需要在请求头添加：
```
Authorization: Bearer <JWT_TOKEN>
```

### Redis 缓存配置

```yaml
eap:
  llm:
    cache-enabled: true        # 是否启用缓存
    cache-ttl-seconds: 3600    # 缓存 TTL（秒）= 1小时
```

### Kafka 事件

任务状态流转会发布到 Topic：`agent-task-events`

事件类型：`CREATED` / `STATUS_CHANGED` / `REVIEWED` / `COMPLETED` / `FAILED`

---

## 扩展指南

### 新增 Agent

1. 在对应模块创建类继承 `BaseAgent`
2. 实现 `getRole()`、`getSystemPrompt()`、`execute()` 方法
3. 添加 `@Component` 注解，`AgentOrchestrator` 自动发现并注册
4. 在 `AgentRole` 枚举中添加新角色

```java
@Component
public class ValidatorAgent extends BaseAgent {
    @Override
    public AgentRole getRole() { return AgentRole.VALIDATOR; }

    @Override
    protected String getSystemPrompt() { return "你是一名数据验证专家..."; }

    @Override
    public AgentResult execute(AgentContext context) { /* 实现逻辑 */ }
}
```

### 新增企业工具

1. 实现 `EnterpriseTool` 接口
2. 添加 `@Component` 注解，`ToolRegistry` 自动注册
3. `ExecutorAgent` 通过 `toolName` 调用

```java
@Component
public class ErpTool implements EnterpriseTool {
    @Override
    public String getToolName() { return "queryErpData"; }

    @Override
    public String execute(String params) { /* 实现 ERP 查询 */ }
}
```

### 新增 LLM Provider

1. 添加 Spring AI Starter 依赖
2. 在 `application.yml` 中配置新 Provider
3. 实现 `LlmService` 接口并注册为 Bean

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 3.3.5, Spring AI 1.0.0-M3 |
| Agent | 自研四种 Agent + BaseAgent 抽象 |
| LLM | OpenAI GPT-4o / Anthropic Claude / Ollama |
| 数据库 | PostgreSQL 14+ + MyBatis-Plus 3.5.9 |
| 缓存 | Redis 6+ (Spring Data Redis) |
| 消息队列 | Kafka 3.x (Spring Kafka) |
| 安全 | Spring Security + JJWT 0.12.6 |
| API 文档 | SpringDoc OpenAPI 2.6.0 (Swagger UI) |
| 监控 | Spring Actuator + Micrometer + Prometheus |
| 构建 | Maven 3.9+，多模块父 POM |

---

## 许可证

MIT License © 2024 Enterprise Agent Platform Team
