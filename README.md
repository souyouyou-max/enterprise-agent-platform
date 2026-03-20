# Enterprise Agent Platform (EAP)

> 机构风险远程管理机器人 | 企业级多 Agent 系统 | 五层分层架构 | Spring AI | Java 17+

[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-blue)](https://spring.io/projects/spring-cloud)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M2-blue)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 项目简介

Enterprise Agent Platform（EAP）是以**机构风险远程管理机器人**为核心业务场景的生产就绪型多 Agent 系统。系统采用 **common → data → engine → business → app 五层架构**，通过多个专职智能体协作完成机构稽核与风险管理任务，支持多模态文档理解（OCR / 图文理解）、RAG 知识问答、自然语言 BI 等能力。

---

## 架构总览

### 五层模块结构

```
┌─────────────────────────────────────────────────────────┐
│  eap-app  :8081（主应用，所有业务模块打包进单体部署）           │
│  eap-gateway  :8080（Spring Cloud Gateway，统一路由入口）    │
│  eap-scheduler  :8085（定时稽核调度服务）                   │
│  bid-analysis-service  :8099（Python FastAPI 文件相似度）   │
└─────────────────────────────────────────────────────────┘
                          ↑ 依赖
┌─────────────────────────────────────────────────────────┐
│  eap-business（业务层）                                   │
│  ├── eap-business-chat      AI 交互中心 + 多模态工具 API   │
│  ├── eap-business-task      Agent 任务管理（Planner/Executor/Reviewer）│
│  ├── eap-business-screening 招采稽核引擎                  │
│  └── eap-business-report    风险报告生成                  │
└─────────────────────────────────────────────────────────┘
                          ↑ 依赖
┌─────────────────────────────────────────────────────────┐
│  eap-engine（引擎层）                                     │
│  ├── eap-engine-llm         Spring AI LLM / Embedding 封装│
│  ├── eap-engine-rag         RAG 知识库 + NL2BI 数据洞察   │
│  ├── eap-engine-rule        招采稽核规则引擎               │
│  └── eap-engine-agent/      Agent 框架                   │
│      ├── eap-engine-agent-core    基础 Agent 框架         │
│      ├── eap-engine-agent-clue    线索发现 Agent          │
│      ├── eap-engine-agent-risk    风险透视 Agent          │
│      └── eap-engine-agent-monitor 监测预警 Agent          │
└─────────────────────────────────────────────────────────┘
                          ↑ 依赖
┌─────────────────────────────────────────────────────────┐
│  eap-data（数据层）                                       │
│  ├── eap-data-repository    实体 / Mapper / 企业工具注册中心│
│  └── eap-data-ingestion     数据源适配接口                │
└─────────────────────────────────────────────────────────┘
                          ↑ 依赖
┌─────────────────────────────────────────────────────────┐
│  eap-common（公共基础层）                                  │
│  LlmService / EmbeddingService 接口、枚举、异常、统一响应体  │
└─────────────────────────────────────────────────────────┘
```

### 请求链路

```
Client
  │ HTTP
  ▼
eap-gateway :8080  ──路由──▶  eap-app :8081
                                │
                    ┌───────────┼────────────┐
                    ▼           ▼            ▼
             InteractionCenterAgent   AgentTaskController
             （多模态 / 知识问答）     （Agent 任务 Pipeline）
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
    img2text     Dazhi OCR   RAG / NL2BI
  （正言平台）   （大智部）    （本地引擎）
```

---

## 智能体一览

| 智能体 | 模块 | 职责 |
|--------|------|------|
| `InteractionCenterAgent` | eap-business-chat | 多轮对话入口，意图识别，自动路由到各 Agent 或工具 |
| `PlannerAgent` | eap-business-task | 将自然语言目标拆解为可执行子任务列表 |
| `ExecutorAgent` | eap-business-task | 按计划调用工具，逐步执行子任务 |
| `ReviewerAgent` | eap-business-task | 对执行结果打分（0-100），低分触发重试 |
| `CommunicatorAgent` | eap-business-report | 聚合多 Agent 结果，生成结构化报告 |
| `ClueDiscoveryAgent` | eap-engine-agent-clue | 采购/财务/合同三主题疑点线索扫描 |
| `RiskAnalysisAgent` | eap-engine-agent-risk | 经营/合规/财务/采购四维度量化风险评分 |
| `MonitoringAgent` | eap-engine-agent-monitor | 阈值规则管理，红/橙/黄三级预警推送 |
| `InsightAgent` | eap-business-chat | 自然语言 → SQL → 执行 → LLM 分析 |

---

## 企业工具（EnterpriseTool）

所有工具实现 `EnterpriseTool` 接口，由 `ToolRegistry` 通过 Spring 自动发现注册，Agent 按工具名调用。

| 工具名 | 类 | 说明 |
|--------|----|------|
| `salesData` | `SalesDataTool` | 按部门/季度查询销售数据 |
| `employee` | `EmployeeTool` | 员工信息查询 |
| `crm` | `CrmTool` | CRM 客户数据查询 |
| `callZhengyanImg2Text` | `ZhengyanPlatformTool` | 正言平台图文理解（OCR + 语义） |
| `callDazhiOcrGeneral` | `DazhiOcrTool` | 大智部通用 OCR（结构化文字提取） |
| `generateSql` | `SqlGeneratorTool` | 自然语言生成 SQL |
| `zhengyanTextClassification` | `ZhengyanTextClassificationTool` | 正言文本分类 |

### 自动 OCR 路由（auto-ocr）

`POST /api/v1/enterprise/semantics/auto-ocr` 由 LLM 根据用户描述自动在两个引擎之间选择：

- **img2text**：适合语义理解、公章识别、文档摘要
- **dazhi-ocr**：适合结构化字段提取（身份证、票据、病历等）

两种引擎的响应均在后端统一提取为 `content` 字段返回，前端无需感知引擎差异。

---

## 主要 API

### 企业工具 API（`/api/v1/enterprise`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/semantics/auto-ocr` | 智能选择 img2text 或大智部 OCR |
| POST | `/semantics/img2text` | 正言平台图文理解 |
| POST | `/ocr/general` | 大智部通用 OCR（支持多页附件） |
| POST | `/semantics/professional-qa` | 正言专业问答 |
| GET | `/sales/{dept}` | 销售数据查询 |
| GET | `/employees/{id}` | 员工信息查询 |
| GET | `/crm/{customerId}` | CRM 客户数据 |
| GET | `/tools` | 查询已注册工具列表 |

### Agent 任务 API（`/api/v1/tasks`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建 Agent 任务 |
| GET | `/{taskId}` | 查询任务状态与结果 |
| POST | `/{taskId}/retry` | 重试失败任务 |
| GET | `/{taskId}/report` | 获取生成的风险报告 |

### 知识问答 API（`/api/v1/knowledge`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/documents` | 录入文档（触发 Embedding） |
| POST | `/search` | 语义检索 |
| POST | `/qa` | RAG 问答 |

### 数据洞察 API（`/api/v1/insight`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/query` | 自然语言 → SQL → 执行 → LLM 分析 |

---

## 外部服务集成

| 服务 | 协议 | 说明 | 配置键 |
|------|------|------|--------|
| 正言平台 img2text | HTTP | 图文理解，支持多图批量 | `eap.tools.zhengyan.platform.endpoints.img2text` |
| 正言平台专业问答 | HTTP | 专业领域 QA | `eap.tools.zhengyan.platform.endpoints.professional-qa` |
| 大智部通用 OCR | HTTP | 结构化文字识别 | `eap.tools.dazhi.ocr.general-url` |
| LLM（OpenAI 兼容） | HTTP | 支持 OpenAI / Qwen / Ollama 等 | `spring.ai.openai.base-url` |
| MinIO | S3 | 文档对象存储 | `eap.tools.minio.*` |
| 稽核分析服务 | HTTP (Feign) | Python FastAPI :8099，文件相似度分析 | `bid-analysis-service` |

---

## 基础设施依赖

| 组件 | 版本/说明 | 用途 |
|------|-----------|------|
| OceanBase | Oracle 方言 | 主数据库（任务、线索、报告持久化） |
| Redis | 6+ | LLM 响应缓存（TTL 1h） |
| Kafka | 3+ | Agent 任务状态事件流 |
| MinIO | S3 兼容 | 文档对象存储 |
| Nacos | 配置中心 | 多环境配置管理（可选） |

---

## 快速启动

### 前置条件

- JDK 17+
- Maven 3.9+
- OceanBase / PostgreSQL（本地可用 PostgreSQL 替代）
- Redis
- Kafka（可选，任务状态事件用）
- Python 3.10+（bid-analysis-service 用）

### 环境变量

```bash
# 数据库
export DB_HOST=localhost
export DB_PORT=2883
export DB_USERNAME=eap_user
export DB_PASSWORD=your_password

# Redis
export REDIS_HOST=localhost

# Kafka（可选）
export KAFKA_SERVERS=localhost:9092

# LLM（OpenAI 兼容接口）
export SPRING_AI_OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_BASE_URL=http://your-llm-endpoint

# 正言平台
export ZHENGYAN_API_KEY=your_zhengyan_key

# 大智部 OCR（appCode 可保持默认）
export DAZHI_OCR_APP_CODE=G209-GHQ-CLM-JIHEFENGXIANJIQIREN

# 安全（生产环境开启）
export SECURITY_ENABLED=true
export JWT_SECRET=your-256-bit-secret
```

### 构建与启动

```bash
# 构建所有模块
mvn clean package -DskipTests

# 启动主应用（端口 8081）
java -jar eap-app/target/eap-app-*.jar

# 启动网关（端口 8080，可选）
java -jar eap-gateway/target/eap-gateway-*.jar

# 启动调度服务（端口 8085，可选）
java -jar eap-scheduler/target/eap-scheduler-*.jar

# 启动 Python 分析服务（端口 8099，可选）
cd bid-analysis-service
pip install -r requirements.txt
uvicorn main:app --port 8099
```

### API 文档

启动后访问：`http://localhost:8081/swagger-ui.html`

---

## 配置说明

主配置文件：`eap-app/src/main/resources/application.yml`

### 关键配置项

```yaml
eap:
  tools:
    zhengyan:
      platform:
        enabled: true
        timeout-ms: 600000          # 图文理解超时（毫秒）
        img2text:
          max-images-per-call: 10   # 单次批量最多图片数
    dazhi:
      ocr:
        enabled: true
        general-url: https://bigdata-ocr-cluster.sinosig.com/general_recognition
        timeout-ms: 20000           # OCR 请求读取超时
    minio:
      enabled: true
  http:
    connect-timeout-ms: 5000        # HTTP 连接超时（所有外部工具共用）
  security:
    enabled: false                  # 生产环境设为 true
```

### 日志级别建议

```yaml
logging:
  level:
    com.enterprise.agent: INFO
    # 需要请求体调试时开启，base64 字段会自动截断
    org.springframework.web.client.RestTemplate: DEBUG
```

> RestTemplate 的 DEBUG 日志内置了 base64 字段截断拦截器（`Base64TruncatingInterceptor`），开启后不会有大量 base64 数据刷屏。

---

## 项目结构

```
enterprise-agent-platform/
├── eap-common/                     # 公共基础：接口、枚举、异常、响应体
├── eap-data/
│   ├── eap-data-repository/        # 实体、Mapper、ToolRegistry、工具实现
│   └── eap-data-ingestion/         # 数据源适配
├── eap-engine/
│   ├── eap-engine-llm/             # Spring AI 封装（LLM + Embedding + Redis 缓存）
│   ├── eap-engine-rag/             # RAG 知识库 + NL2BI 数据洞察
│   ├── eap-engine-rule/            # 招采稽核规则引擎
│   └── eap-engine-agent/
│       ├── eap-engine-agent-core/  # BaseAgent、AgentContext、AgentOrchestrator
│       ├── eap-engine-agent-clue/  # 线索发现 Agent
│       ├── eap-engine-agent-risk/  # 风险透视 Agent
│       └── eap-engine-agent-monitor/ # 监测预警 Agent
├── eap-business/
│   ├── eap-business-chat/          # AI 交互中心（多模态 + 多轮对话）
│   ├── eap-business-task/          # Agent 任务 Pipeline（Planner/Executor/Reviewer）
│   ├── eap-business-screening/     # 招采稽核引擎
│   └── eap-business-report/        # 风险报告生成
├── eap-gateway/                    # Spring Cloud Gateway :8080
├── eap-scheduler/                  # 定时稽核调度 :8085
├── eap-app/                        # 主应用入口 :8081
├── bid-analysis-service/           # Python FastAPI 文件相似度分析 :8099
└── database/                       # 数据库初始化脚本
```

---

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17+ |
| 框架 | Spring Boot | 4.0.3 |
| AI | Spring AI | 2.0.0-M2 |
| 微服务 | Spring Cloud | 2025.1.1 |
| 配置中心 | Spring Cloud Alibaba (Nacos) | 2025.1.0.0 |
| ORM | MyBatis Plus | 3.5.15 |
| 数据库 | OceanBase（Oracle 方言） | — |
| 缓存 | Redis (Lettuce) | — |
| 消息队列 | Kafka | — |
| 对象存储 | MinIO | — |
| HTTP 客户端 | Spring RestTemplate | — |
| 认证 | Spring Security + JWT (jjwt) | 0.12.6 |
| API 文档 | SpringDoc OpenAPI | 2.6.0 |
| 可观测 | Actuator + Prometheus | — |
| Python 服务 | FastAPI + Uvicorn | — |

---

## 注意事项

- **敏感配置**：API Key、数据库密码等均通过环境变量注入，不要将真实凭证提交到代码仓库。
- **安全开关**：`eap.security.enabled` 默认 `false`（开发模式），生产环境必须设为 `true` 并配置正确的 JWT secret。
- **OCR 白名单**：大智部 OCR 接口对调用方 IP 有白名单限制，需将应用服务器 IP 加入白名单。
- **OceanBase 方言**：MyBatis Plus 使用 Oracle 方言，本地开发可替换为 PostgreSQL（修改 datasource url 和 driver）。
