# eap-engine-rag

> RAG 知识检索模块，提供基于向量相似度的知识问答能力，以及自然语言驱动的 NL2BI 数据洞察分析。

## 职责

- 对知识文档进行向量化并存储，支持相似度检索
- 基于 RAG（Retrieval-Augmented Generation）实现知识问答
- 实现 NL2BI 三步流水线：自然语言 → SQL → 查询执行 → LLM 分析

## 包含内容

### 知识问答（`com.enterprise.agent.dataservice.knowledge`）

#### 实体

| 类 | 说明 |
|----|------|
| `KnowledgeDocument` | 知识文档实体：id / title / content / category / source / embedding_vector / createdAt / updatedAt |

#### 服务

| 类 | 方法 | 说明 |
|----|------|------|
| `KnowledgeIndexService` | `indexDocument(doc)` / `updateIndex()` / `deleteDocument(docId)` | 向量化索引管理 |
| `EmbeddingServiceImpl` | `embed(text)` | 调用 `SpringAiEmbeddingService` 生成向量 |
| `KnowledgeQaService` | `answer(question)` | RAG 问答：相似度检索 → LLM 上下文增强生成 |

#### 向量化流程

```
文档入库
  ↓
EmbeddingServiceImpl.embed(content)  →  float[] 向量
  ↓
KnowledgeRepository（向量存储）
  ↓
余弦相似度检索（Top-K 最相关文档）
  ↓
拼接检索结果 + 用户问题 → LLM 生成答案
```

### NL2BI 数据洞察（`com.enterprise.agent.dataservice.insight`）

#### 模型

| 类 | 说明 |
|----|------|
| `InsightRequest` | 请求：question / sqlContext / filters |
| `InsightResult` | 结果：question / generatedSql / rawData / analysis / chartHint / success / errorMessage |

#### 服务

| 类 | 方法 | 说明 |
|----|------|------|
| `NlToSqlService` | `generateSql(question, context)` | LLM 将自然语言转换为 SQL |
| `DataQueryService` | `executeQuery(sql)` | 安全执行 SELECT（阻断 INSERT/UPDATE/DELETE） |
| `InsightAnalysisService` | `analyze(question, rawData)` / `extractChartHint(analysis)` | LLM 分析查询结果，提取图表类型建议 |

#### NL2BI 三步流水线

```
用户问题："各部门今年的采购金额分布？"
  ↓ Step 1: NlToSqlService
SELECT department, SUM(amount) FROM procurement_project WHERE ...
  ↓ Step 2: DataQueryService
[{"department":"采购部","sum":1200000}, ...]
  ↓ Step 3: InsightAnalysisService
"采购部占比最高（42%），建议关注集中度风险..." + chartHint: "PIE"
```

## 依赖关系

- 依赖：`eap-common`、`eap-engine-llm`
- 被依赖：`eap-business-chat`（`InsightAgent`、`InteractionCenterAgent`）

## 快速使用

```java
// 知识问答
@Autowired KnowledgeQaService qaService;
String answer = qaService.answer("什么情况下需要强制招标？");

// 数据洞察（NL2BI）
@Autowired NlToSqlService nlToSqlService;
@Autowired DataQueryService dataQueryService;
@Autowired InsightAnalysisService analysisService;

String sql = nlToSqlService.generateSql("各季度采购金额趋势", tableContext);
List<Map<String, Object>> data = dataQueryService.executeQuery(sql);
String analysis = analysisService.analyze("各季度采购金额趋势", data);
```
