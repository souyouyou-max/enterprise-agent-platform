# eap-business-pipeline

> OCR 流水线模块，负责文件批次提交、多引擎 OCR 识别、多模态语义分析、文件相似度对比及定时补偿调度，实现从原始文件到结构化分析结果的完整自动化链路。

## 职责

- 管理 OCR 批次生命周期：PENDING → OCR_PROCESSING → OCR_DONE → ANALYZED → COMPARED / FAILED
- 对接正言（Zhengyan）多模态平台和大智部（Dazhi）通用 OCR 两套识别引擎
- 调用 LLM 对 OCR 结果做结构化语义分析，落库 `OcrFileAnalysis`
- 计算批次内文件间相似度，落库 `FileSimilarityResult`
- 暴露 HTTP API 供前端和其他服务调用
- 定时补偿调度（`OcrPipelineScheduler`），自动重试卡在中间态的批次

## 流水线阶段

```
文件提交（SubmitBatch）
  ↓  [PENDING]
OCR 识别（OcrPipelineService → OcrRecognitionService）
  ↓  [OCR_PROCESSING → OCR_DONE]
语义分析（MultimodalService → PipelineLlmAnalysisDomainService）
  ↓  [ANALYZED]
相似度对比（FileSimilarityService → BidAnalysisClient）
  ↓  [COMPARED]
完成 ✓
```

## 包含内容

### 配置（`com.enterprise.agent.business.pipeline.config`）

| 类 | 说明 |
|----|------|
| `EapPipelineProperties` | 流水线全局配置（前缀 `eap.pipeline`）：OCR 开关、分析开关、对比开关、调度参数、提示词模板 |
| `PipelineEffectiveConfig` | record 类型，保存合并后的有效配置（全局配置 + extraInfo 覆盖） |
| `PipelineEffectiveConfigResolver` | 按批次 extraInfo 解析有效配置，支持运行时动态覆盖 |
| `PipelineEnv` | 环境常量（OCR 引擎枚举等） |
| `PipelineMultimodalPromptResolver` | 根据文档类型动态选择多模态分析 Prompt |
| `PipelineConfiguration` | `@EnableConfigurationProperties` 入口 |

### REST API（`com.enterprise.agent.business.pipeline.controller`）

#### OcrPipelineController（`/api/v1/pipeline`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/submit` | 提交文件批次，返回 batchNo |
| POST | `/trigger-ocr` | 手动触发指定批次 OCR |
| POST | `/trigger-analysis` | 手动触发指定批次语义分析 |
| POST | `/trigger-compare` | 手动触发指定批次相似度对比 |
| GET | `/status/{batchNo}` | 查询批次状态和文件进度 |

#### FileSimilarityController（`/api/v1/similarity`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/result/{batchNo}` | 获取批次文件相似度对比结果 |

#### EnterpriseToolController（`/api/v1/enterprise`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/semantics/img2text` | 正言图文理解（img2text），单次调用落库 |
| POST | `/semantics/auto-ocr` | 智能 OCR 路由，LLM 自动选择正言或大智部引擎 |
| POST | `/ocr/general` | 大智部通用 OCR，支持单图或多页文件 |

### 服务层（`com.enterprise.agent.business.pipeline.service`）

| 接口/类 | 说明 |
|---------|------|
| `OcrPipelineService` | 流水线主服务，编排各阶段；内置 `BatchProgressView`、`FileStatusSummary` 等 record |
| `OcrRecognitionService` | OCR 识别入口接口：`recognize()` / `recognizeFromResult()` |
| `OcrRecognitionRequest` | OCR 请求 Builder，封装 businessNo / source / fileName / prompt 等字段 |
| `MultimodalService` | 多模态能力接口：`autoOcr()` 智能路由、`img2text()`、`dazhiOcrGeneral()` |
| `DocumentImageConverter` | 文档转图片工具（PDF/Office → Base64 图片列表） |
| `FileSimilarityService` | 相似度计算服务，调用 `BidAnalysisClient` 对比批次内文件 |

### 领域服务（`com.enterprise.agent.business.pipeline.service.impl`）

| 类 | 说明 |
|----|------|
| `OcrPipelineServiceImpl` | 流水线主逻辑：阶段推进、状态管理、失败容忍（`failToleranceRatio`） |
| `OcrRecognitionServiceImpl` | OCR 识别实现：路由至正言/大智部，结果落库 `OcrFileMain`/`OcrFileSplit` |
| `MultimodalServiceImpl` | 多模态实现：img2text 分页批次调用、autoOcr 路由、大智部通用识别 |
| `PipelineLlmAnalysisDomainService` | LLM 分析领域服务：正言请求体构建、分页结果回写 split、结构化分析落库 |

### 调度器（`com.enterprise.agent.business.pipeline.scheduler`）

`OcrPipelineScheduler`：定时扫描所有处于中间态（PENDING / OCR_PROCESSING / OCR_DONE / ANALYZED）超时的批次，自动触发下一阶段或标记失败，避免任务永久卡住。

## 关键配置项

```yaml
eap:
  pipeline:
    fail-tolerance-ratio: 0.5      # 允许失败文件比例（0~1）
    ocr:
      enabled: true
    analysis:
      enabled: true
      max-images-per-file: 4       # 单文件每次多模态调用最大图片数
      max-total-analysis-pages: 100
    compare:
      enabled: true
    scheduler:
      enabled: true
      interval-ms: 60000           # 补偿调度间隔（毫秒）
      pending-stale-seconds: 300
      processing-stale-seconds: 600
      done-stale-seconds: 300
```

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`、`eap-engine-tools`（ZhengyanPlatformTool、DazhiOcrTool）、`eap-engine-llm`、`eap-engine-rule`（BidAnalysisClient）
- 被依赖：`eap-app`（运行时装配）、`eap-app/PipelineOpsPlaybookService`（读取配置类）

## 示例

```bash
# 提交批次
curl -X POST http://localhost:8081/api/v1/pipeline/submit \
  -H "Content-Type: application/json" \
  -d '{"batchNo":"BATCH001","files":[{"fileName":"invoice.pdf","fileBase64":"..."}]}'

# 查询批次状态
curl http://localhost:8081/api/v1/pipeline/status/BATCH001

# 直接调用 img2text
curl -X POST http://localhost:8081/api/v1/enterprise/semantics/img2text \
  -H "Content-Type: application/json" \
  -d '{"text":"请抽取发票信息","attachments":[{"name":"inv.jpg","mimeType":"image/jpeg","base64":"..."}]}'
```
