# Enterprise Agent Platform (EAP)

> 机构风险远程管理机器人 | 企业级 Agent 系统 | 分层架构 | 微服务部署 | 多 Agent 协作 | Spring AI | Java 17+

[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.x-blue)](https://spring.io/projects/spring-cloud)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M3-blue)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

## 项目简介

Enterprise Agent Platform (EAP) 是以**机构风险远程管理机器人**为核心业务场景的生产就绪型多 Agent 系统。系统采用 **common / data / engine / gateway / service 分层架构**，通过四个专职智能体协作完成机构稽核与风险管理任务，并以 **Spring Cloud 微服务**方式独立部署。

### 四智能体协作架构

| 智能体 | 职责 | 核心能力 |
|--------|------|----------|
| **AI交互中心** (`InteractionCenterAgent`) | 统一对话入口，意图识别与路由 | 自然语言理解，自动调用其他三个 Agent |
| **线索发现** (`ClueDiscoveryAgent`) | 各审计主题疑点筛查 | 采购/财务/合同三主题线索扫描，按风险等级分类 |
| **风险透视分析** (`RiskAnalysisAgent`) | 全国机构多维风险评分 | 经营/合规/财务/采购四维度量化评分，综合报告生成 |
| **监测预警** (`MonitoringAgent`) | 风险量化评分与动态预警 | 阈值规则管理，红/橙/黄三级预警推送 |

**平台能力：**
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
│         API 网关（eap-gateway :8080，Spring Cloud Gateway）    │
│   POST /api/v1/tasks  GET /api/v1/chat  /api/v1/knowledge …  │
└────────────────────────┬─────────────────────────────────────┘
                         │ 路由转发
┌────────────────────────▼─────────────────────────────────────┐
│                   Agent Core Layer                            │
│                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────┐│
│  │PlannerAgent │→ │ExecutorAgent │→ │ReviewerAgent │→ │Comm││
│  │目标→子任务   │  │子任务→工具调用│  │质量评分0-100  │  │报告││
│  └─────────────┘  └──────────────┘  └──────────────┘  └────┘│
│                                                              │
│    AgentOrchestrator（Pipeline 编排）+ AgentDispatcher        │
│    InteractionCenterAgent（意图识别 + 多 Agent 路由）           │
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
│  知识库（PostgreSQL embedding，eap-knowledge RAG）             │
│  数据仓库（sales_data / employee / crm_order，eap-insight）    │
└──────────────────────────────────────────────────────────────┘
```

### 微服务部署视图

```
┌──────────────────────────────────────────────────────────────┐
│  公共库（编译依赖，不独立部署）                                    │
│  eap-common  eap-data                                        │
│  eap-engine-llm                                              │
│  eap-engine-agent-core  eap-engine-agent-impl                │
│  eap-engine-data-service                                     │
└──────────────────────────────────────────────────────────────┘
                         │ 打包进各微服务
┌──────────────────────────────────────────────────────────────┐
│  微服务（独立部署）                                              │
│                                                              │
│  eap-gateway         :8080  API 网关，统一入口                 │
│  eap-service-agent   :8081  Agent 任务服务                    │
│  eap-service-knowledge :8082  知识问答服务                     │
│  eap-service-insight :8083  数据洞察服务                       │
│  eap-service-chat    :8084  AI 交互中心服务                    │
└──────────────────────────────────────────────────────────────┘
```

---

## 微服务服务列表

### 服务一览

| 服务 | 端口 | 描述 |
|------|------|------|
| eap-gateway | 8080 | API 网关（Spring Cloud Gateway），路由转发，统一入口 |
| eap-service-agent | 8081 | Agent 任务服务：任务创建/查询/重试/报告 + 企业工具 API |
| eap-service-knowledge | 8082 | 知识问答服务：文档录入、语义检索、RAG 问答 |
| eap-service-insight | 8083 | 数据洞察服务：自然语言 → SQL → 执行 → LLM 分析 |
| eap-service-chat | 8084 | AI 交互中心：多轮对话统一入口，意图识别自动路由 |

### 服务间调用关系

```
                    ┌─────────────────────────────┐
                    │       Client（浏览器/App）     │
                    └──────────────┬──────────────┘
                                   │ HTTP
                    ┌──────────────▼──────────────┐
                    │      eap-gateway :8080        │
                    │   Spring Cloud Gateway        │
                    │  路由规则：                    │
                    │  /api/v1/tasks/**  → :8081    │
                    │  /api/v1/knowledge/** → :8082 │
                    │  /api/v1/insight/**  → :8083  │
                    │  /api/v1/chat/**     → :8084  │
                    └──┬──────┬──────┬──────┬──────┘
                       │      │      │      │  lb://
          ┌────────────▼─┐ ┌──▼──┐ ┌▼────┐ ┌─▼────────────┐
          │ eap-service- │ │     │ │     │ │ eap-service- │
          │    agent     │ │know-│ │insi-│ │    chat      │
          │    :8081     │ │ledge│ │ght  │ │    :8084     │
          │              │ │:8082│ │:8083│ │              │
          │ - 任务CRUD    │ │     │ │     │ │ - 多轮对话    │
          │ - Agent流水线 │ │-RAG │ │-NL  │ │ - 意图识别    │
          │ - 企业工具    │ │-向量│ │ 2BI │ │ - 路由分发    │
          └──────────────┘ └─────┘ └─────┘ └──────┬───────┘
                 │               │          Feign  │
                 │         ┌─────┴──────────────   │
                 │         │   跨服务 Feign 调用   ◄─┘
                 └────────►│  AgentServiceClient   │
                           │ KnowledgeServiceClient │
                           └───────────────────────┘
```

### Nacos 服务注册

所有微服务默认关闭 Nacos 注册（`NACOS_ENABLED=false`），本地开发直连。
生产环境设置 `NACOS_ENABLED=true` 与 `NACOS_ADDR=<nacos地址>` 启用服务发现。

---

## 项目结构

> 对齐 audit-intelligence-platform 风格的五层分层架构（common / data / engine / business / app）

```
enterprise-agent-platform/
├── pom.xml                                       # 根 POM（Spring Boot 3.3 + Spring AI 1.0 + Spring Cloud 2023）
│
├── ── 公共基础层（common）─────────────────────────────────────
├── eap-common/                                   # 枚举/异常/统一响应/LlmService接口抽象
│
├── ── 数据层（data）────────────────────────────────────────────
├── eap-data/                                     # 数据层父模块（pom）
│   ├── eap-data-repository/                      # 实体 + Mapper + AgentTaskDataService + 企业工具
│   │   └── src/.../                              # entity/ mapper/ service/ tools/
│   └── eap-data-ingestion/                       # 外部数据源适配器（招采/费控/EHR/企查查）
│       └── src/.../data/adapter/                 # DataSourceAdapter + 4个实现
│
├── ── 引擎层（engine）──────────────────────────────────────────
├── eap-engine/                                   # 引擎层父模块（pom）
│   ├── eap-engine-llm/                           # LLM：Spring AI 多模型热切换 + Redis缓存
│   ├── eap-engine-rag/                           # RAG知识检索问答 + NL2BI数据洞察
│   ├── eap-engine-rule/                          # SQL规则引擎（AuditRule接口 + 四大规则实现）
│   └── eap-engine-agent/                         # Agent 父模块（pom）
│       ├── eap-engine-agent-core/                # Agent核心抽象：BaseAgent/Context/Orchestrator
│       ├── eap-engine-agent-clue/                # 线索发现Agent（com.enterprise.agent.engine.agent.clue）
│       ├── eap-engine-agent-risk/                # 风险透视Agent（com.enterprise.agent.engine.agent.risk）
│       └── eap-engine-agent-monitor/             # 监测预警Agent（com.enterprise.agent.engine.agent.monitor）
│
├── ── 业务层（business）────────────────────────────────────────
├── eap-business/                                 # 业务层父模块（pom）
│   ├── eap-business-task/                        # 任务管理：Planner/Executor/Reviewer + 任务CRUD + Pipeline
│   ├── eap-business-screening/                   # 疑点筛查：ProcurementAuditAgent + AuditEngineService
│   ├── eap-business-report/                      # 报告生成：CommunicatorAgent + 风险管理接口
│   └── eap-business-chat/                        # AI交互中心：InteractionCenterAgent + 多轮对话
│
├── ── 网关层（gateway）─────────────────────────────────────────
├── eap-gateway/                                  # API 网关（端口 8080，Spring Cloud Gateway）
│
├── ── 调度服务（scheduler）─────────────────────────────────────
├── eap-scheduler/                                # 定时调度服务（端口 8085，审计引擎定时驱动）
│
└── ── 主应用（app）─────────────────────────────────────────────
    └── eap-app/                                  # 主应用（端口 8081，整合所有业务模块）
```

### 五层架构依赖关系

```
eap-app / eap-scheduler（可执行）
    ↑
eap-business-{task|screening|report|chat}（业务层）
    ↑
eap-engine-{llm|rag|rule|agent-*}（引擎层）
    ↑
eap-data-{repository|ingestion}（数据层）
    ↑
eap-common（公共层）
```

### 端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| eap-gateway | 8080 | API 网关，统一路由到 eap-app |
| eap-app | 8081 | 主应用，提供所有 REST API |
| eap-scheduler | 8085 | 定时调度服务，审计引擎驱动 |

---

## AI 交互中心

### 架构角色映射

```
┌─────────────────────────────────────────────────────────────────┐
│               AI 交互中心（InteractionCenterAgent）               │
│                    统一入口 / 意图识别 / 路由                      │
└───────┬─────────────────┬────────────────┬──────────────────────┘
        │                 │                │
        ▼                 ▼                ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│  线索发现      │ │  风险透视      │ │  监测预警      │
│  (Planner     │ │  (Knowledge   │ │  (Insight     │
│   Pipeline)   │ │   QA / RAG)   │ │   / NL2BI)    │
└───────────────┘ └───────────────┘ └───────────────┘
```

| 业务场景 | 对应 Agent | 触发关键词示例 |
|---------|-----------|--------------|
| 线索发现 / 流程自动化 | PlannerAgent → ExecutorAgent → ReviewerAgent → CommunicatorAgent | "帮我分析…并生成报告"、"制定计划"、"执行任务" |
| 风险透视 / 知识问答 | KnowledgeQaService（RAG） | "年假怎么申请"、"公司政策"、"规章制度" |
| 监测预警 / 数据洞察 | InsightAgent（NL2BI） | "哪个部门销售额最高"、"统计…数据"、"本季度趋势" |
| 一般问答 | LLM 直接对话 | 问候、概念解释、闲聊 |

### 多轮对话流程

```
用户发送消息
      │
      ▼
POST /api/v1/chat  {sessionId, message}
      │
      ▼
InteractionCenterAgent.chat()
      │
      ├─ [1] sanitizeInput()          # 防 Prompt 注入清洗
      │
      ├─ [2] ConversationSession      # 写入用户消息（滑动窗口 20条）
      │
      ├─ [3] detectIntent()           # LLM 意图分类
      │       └─ 输出：PLANNING / KNOWLEDGE / INSIGHT / GENERAL
      │
      ├─ [4] 路由执行
      │       ├─ PLANNING   → orchestrator.runPipeline()
      │       │               Planner→Executor→Reviewer→Communicator
      │       ├─ KNOWLEDGE  → KnowledgeQaService.answer()
      │       ├─ INSIGHT    → InsightAgent.investigate()
      │       └─ GENERAL    → LlmService（带历史上下文）
      │
      ├─ [5] ConversationSession      # 写入助手回复
      │
      └─ [6] 返回 InteractionResult
               {sessionId, agentType, response, usedTools, timestamp}
```

### 对话 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat` | 发送消息（核心对话接口） |
| POST | `/api/v1/chat/session` | 创建新会话，返回 `{sessionId}` |
| DELETE | `/api/v1/chat/session/{sessionId}` | 清除会话历史 |
| GET | `/api/v1/chat/session/{sessionId}/history` | 获取历史消息列表 |

```bash
# 1. 创建会话
curl -X POST http://localhost:8080/api/v1/chat/session
# → {"sessionId": "550e8400-e29b-41d4-a716-446655440000"}

# 2. 多轮对话（意图识别自动路由）
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"550e8400-e29b-41d4-a716-446655440000","message":"分析本季度华南区销售数据"}'
# → agentType: INSIGHT，调用 InsightAgent（NL2BI）

curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"550e8400-e29b-41d4-a716-446655440000","message":"年假怎么申请？"}'
# → agentType: KNOWLEDGE，调用 KnowledgeQaService（RAG）

# 3. 查看历史
curl http://localhost:8080/api/v1/chat/session/550e8400-e29b-41d4-a716-446655440000/history
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

## 四大业务场景

### 场景一：流程自动化

通过 Planner → Executor → Reviewer → Communicator 四 Agent 流水线，将自然语言业务目标自动分解为子任务、调用企业工具执行、质量审查、生成 Markdown 报告。

```
POST /api/v1/tasks  {"goal": "分析Q4华南区销售情况并生成建议"}
                │
      PlannerAgent（目标→3-5个子任务）
                │
      ExecutorAgent（工具调用 + 3次重试）
                │
      ReviewerAgent（评分 ≥ 60 则通过）
                │
      CommunicatorAgent（EMAIL / SUMMARY / DETAILED 报告）
```

### 场景二：企业知识问答（eap-knowledge）

**技术路线**：用户问题 → 语义向量化 → 余弦相似度检索 → LLM 整合生成 → 输出答案

```
POST /api/v1/knowledge/index   录入文档（向量化 + 持久化到 PostgreSQL）
POST /api/v1/knowledge/ask     知识问答（RAG）
GET  /api/v1/knowledge/documents  文档列表
```

**RAG 架构**：
```
用户问题
    │
EmbeddingServiceImpl.embedSingle()  ← Spring AI EmbeddingModel
    │ 查询向量
KnowledgeIndexService.searchSimilar()
    │ 余弦相似度排序，取 TopK 文档
KnowledgeQaService.answer()
    │ 拼装 Prompt："你是企业知识助手，根据以下文档回答..."
LlmService.chatWithSystem()
    │
答案返回
```

**存储**：知识文档以 JSON 格式将 float 向量存入 `knowledge_document.embedding`，生产环境可替换为 Milvus / PGVector。

### 场景三：智能数据洞察（eap-insight）

**技术路线**：自然语言 → SQL → 执行查询 → LLM 分析 → 生成报告

```
POST /api/v1/insight/analyze   自然语言数据分析（NL2BI）
GET  /api/v1/insight/schema    查看可查询的表结构
```

**NL2BI 架构**（InsightAgent 三步流水线）：
```
用户问题："上季度哪个部门销售额最高？"
    │
NlToSqlService.generateSql()
    │ Prompt："你是SQL专家，根据以下表结构生成PostgreSQL查询..."
    │ 内置 Mock Schema：sales_data / employee / crm_order
    │
DataQueryService.executeQuery()
    │ 安全校验：仅允许 SELECT，自动追加 LIMIT 500
    │ JdbcTemplate 执行
    │
InsightAnalysisService.analyze()
    │ Prompt："你是数据分析师，根据以下数据回答问题并给出洞察..."
    │
InsightResult {generatedSql, rawData, analysis, chartHint}
```

### 场景四：招采稽核（eap-procurement-audit）

**场景背景**：企业采购环节存在应招未招、化整为零、围标串标、利益输送等违规风险，传统人工稽核效率低、覆盖面有限。招采稽核智能体通过自动化数据比对与分析，系统性识别违规线索，辅助合规部门精准锁定高风险项目。

**四个细分场景**：

| 场景 | 检测逻辑 | 风险类型 |
|------|---------|---------|
| 大额采购未招标 | 对比费控合同与招采系统，筛选超门槛且无招采流程的合同 | 合规违规 |
| 化整为零识别 | 统计短期内同一供应商同类项目多笔合同，累计金额接近门槛 | 规避监管 |
| 围标串标识别 | 分析同一项目多家投标文件的 Jaccard 相似度 + 股东关联关系 | 招标舞弊 |
| 利益输送预警 | 比对中标供应商股东/法人/董监高与公司内部员工名单 | 关联交易 |

**稽核流程**：

```
POST /api/v1/procurement-audit/audit/full?orgCode=ORG001
                │
   ProcurementAuditAgent（招采稽核智能体）
                │  LLM 自主决策调用工具
    ┌───────────┼──────────────────────┐
    ↓           ↓            ↓         ↓
detectUntendered  detectSplit  detectCollusive  detectConflict
（大额未招标）   （化整为零）   （围标串标）     （利益输送）
    └───────────┴──────────────────────┘
                │
    汇总稽核报告（风险等级 + 线索清单 + 核查建议）
```

**API 接口**：

```bash
# 全量稽核（运行全部4个场景）
curl -X POST "http://localhost:8080/api/v1/procurement-audit/audit/full?orgCode=ORG001"

# 单场景稽核（untendered / split / collusive / conflict）
curl -X POST "http://localhost:8080/api/v1/procurement-audit/audit/scene?orgCode=ORG001&scene=collusive"

# 查询采购合同列表
curl "http://localhost:8080/api/v1/procurement-audit/contracts?orgCode=ORG001"

# 查询投标记录
curl "http://localhost:8080/api/v1/procurement-audit/bids/BID-PROJECT-001"
```

**典型稽核输出示例**：

```
【场景1：大额采购未招标】发现 3 条疑似大额采购未招标记录（门槛：50万元）：

1. 项目名称：办公楼安防监控系统采购
   供应商：上海安防设备集团
   合同金额：92.00万元
   合同日期：2026-01-12  ⚠️ 风险等级：高

【场景4：利益输送预警】发现 2 条疑似利益输送线索：

线索1  ⚠️ 风险等级：高
  中标供应商：鑫达办公设备有限公司（ID：SUP020）
  关联人员：赵海波（法人，持股：60.0%）
  关联内部员工：EMP-20231（采购部经理）
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
| Docker & Docker Compose | 方式一必需 |

### 方式一：Docker Compose（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/souyouyou-max/enterprise-agent-platform.git
cd enterprise-agent-platform

# 2. 构建所有模块
mvn clean package -DskipTests

# 3. 配置 LLM（编辑 docker-compose.yml 或设置环境变量）
export LLM_PROVIDER=claude
export CLAUDE_API_KEY=sk-ant-xxx
export CLAUDE_BASE_URL=https://cn.xingsuancode.com

# 4. 一键启动
docker-compose up -d

# 查看日志
docker-compose logs -f eap-gateway
docker-compose logs -f eap-service-agent
```

启动后访问：
- 网关（所有 API 统一入口）：http://localhost:8080
- Agent 服务 Swagger：http://localhost:8081/swagger-ui.html
- 知识服务 Swagger：http://localhost:8082/swagger-ui.html
- 洞察服务 Swagger：http://localhost:8083/swagger-ui.html
- 对话服务 Swagger：http://localhost:8084/swagger-ui.html
- Nacos 控制台：http://localhost:8848/nacos

### 方式二：本地分服务启动

```bash
# 1. 启动基础设施（PostgreSQL / Redis / Kafka）

# 2. 初始化数据库
psql -U postgres -c "CREATE USER eap_user WITH PASSWORD 'eap_password';"
psql -U postgres -c "CREATE DATABASE eap_db OWNER eap_user;"
psql -U eap_user -d eap_db -f eap-service-agent/src/main/resources/db/schema.sql

# 3. 配置环境变量
export LLM_PROVIDER=claude           # openai | claude | ollama
export CLAUDE_API_KEY=sk-ant-xxx
export CLAUDE_BASE_URL=https://cn.xingsuancode.com

# 4. 按顺序启动各服务
cd eap-service-agent    && mvn spring-boot:run  # :8081
cd eap-service-knowledge && mvn spring-boot:run # :8082
cd eap-service-insight  && mvn spring-boot:run  # :8083
cd eap-service-chat     && mvn spring-boot:run  # :8084
cd eap-gateway          && mvn spring-boot:run  # :8080（最后启动）
```

### 验证启动

```
# Swagger UI（通过网关）
http://localhost:8080/swagger-ui.html

# Actuator 健康检查
http://localhost:8080/actuator/health

# Prometheus 指标
http://localhost:8080/actuator/prometheus
```

---

## API 接口文档

> 所有接口统一通过网关 `http://localhost:8080` 访问。

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

### 知识问答 API（RAG）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/knowledge/index` | 录入并索引知识文档 |
| POST | `/api/v1/knowledge/ask` | 知识问答（RAG） |
| GET | `/api/v1/knowledge/documents` | 查询全部文档列表 |

```bash
# 录入文档
curl -X POST http://localhost:8080/api/v1/knowledge/index \
  -H "Content-Type: application/json" \
  -d '{"title":"年假政策","content":"员工入职满一年可享受5天年假...","category":"HR"}'

# 知识问答
curl -X POST http://localhost:8080/api/v1/knowledge/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"年假怎么申请？"}'
```

### 数据洞察 API（NL2BI）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/insight/analyze` | 自然语言数据分析 |
| GET | `/api/v1/insight/schema` | 查看可查询的表结构 |

```bash
# 自然语言数据分析
curl -X POST http://localhost:8080/api/v1/insight/analyze \
  -H "Content-Type: application/json" \
  -d '{"question":"上季度哪个部门销售额最高？"}'
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
| claude | `CLAUDE_API_KEY` / `ANTHROPIC_API_KEY` | claude-sonnet-4-6 |
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
| 框架 | Spring Boot 3.2, Spring AI 1.0.0-M3 |
| 微服务 | Spring Cloud 2023.0.x — Gateway / OpenFeign / LoadBalancer |
| 服务发现 | Spring Cloud Alibaba 2023.0.1.0 — Nacos |
| Agent | 自研五种 Agent + BaseAgent 抽象 + AgentDispatcher 调度器 |
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
