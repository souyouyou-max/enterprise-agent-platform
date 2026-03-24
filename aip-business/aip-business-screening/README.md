# aip-business-screening

> 疑点筛查业务模块，集成规则引擎和招采稽核 Agent，覆盖招采合规四大审计场景。

## 职责

- 驱动数据同步（四个数据源适配器）+ 规则扫描（四条规则）的完整稽核流程
- 提供 `ProcurementAuditAgent` 进行 AI 增强的招采疑点分析
- 持久化疑点结果（`ClueResult`）并提供查询接口
- 支持手动触发和定时调度两种稽核模式

## 包含内容

### ProcurementAuditAgent（`com.sinosig.aip.business.screening`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.PROCUREMENT_AUDITOR` |
| 核心能力 | 识别四类招采违规 + LLM 深度分析 |

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | 标准 Agent 接口，按 context.goal 执行稽核 |
| `auditAll(orgCode)` | 执行全量稽核（四大场景） |
| `auditScene(orgCode, scene)` | 执行单场景稽核（untendered/split/collusive/conflict） |

### 四大稽核场景

| 场景标识 | 场景名称 | 检测逻辑 |
|---------|---------|---------|
| `untendered` | 大额未招标 | 50万以上付款无对应采购项目 |
| `split` | 化整为零 | 同一供应商60天内分拆付款规避招标 |
| `collusive` | 围标串标 | 同一项目多家投标企业法人相同 |
| `conflict` | 利益冲突 | 供应商法人/股东与内部员工存在关联 |

### ProcurementAuditToolkit（`com.sinosig.aip.business.screening.toolkit`）

| 工具方法 | 说明 |
|---------|------|
| `detectUntenderedPurchases(orgCode)` | 调用 `UntenderedRule` 执行检测 |
| `identifySplitPurchases(orgCode)` | 调用 `SplitPurchaseRule` 执行检测 |
| `identifyCollusiveBids(projectId)` | 调用 `CollusiveBidRule` 执行检测 |
| `detectConflictOfInterest(orgCode)` | 调用 `ConflictOfInterestRule` 执行检测 |

### AuditEngineService（`com.sinosig.aip.business.screening.service`）

规则引擎核心服务，编排数据同步和规则执行：

| 方法 | 说明 |
|------|------|
| `syncAllDataSources()` | 依次调用四个适配器同步数据 |
| `runAllRules(orgCode)` | 执行四条规则，持久化 `ClueResult` |
| `fullAudit(orgCode)` | 完整流程：`syncAllDataSources()` + `runAllRules(orgCode)` |
| `getPendingClues(orgCode)` | 查询待处理疑点列表 |
| `getAllClues(orgCode)` | 查询全部疑点记录 |

> 去重机制：每次运行前删除该机构的 PENDING 记录，避免重复积累。

### REST API

#### AuditEngineController（`/audit/*`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/audit/sync` | 手动触发数据同步 |
| POST | `/audit/run` | 对指定机构运行全部规则 |
| POST | `/audit/full` | 完整稽核（同步+规则扫描） |
| GET | `/audit/clues/{orgCode}` | 查询机构待处理疑点 |

#### ProcurementAuditController（`/procurement/audit/*`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/procurement/audit/all/{orgCode}` | AI 全场景稽核 |
| POST | `/procurement/audit/{orgCode}/{scene}` | AI 单场景稽核 |

## 依赖关系

- 依赖：`aip-common`、`aip-data-repository`、`aip-data-ingestion`、`aip-engine-rule`、`aip-engine-agent-core`、`aip-engine-llm`
- 被依赖：`aip-business-chat`（`InteractionCenterAgent` 调用 `ProcurementAuditAgent`）

## 快速使用

```bash
# 完整稽核（数据同步 + 规则扫描）
curl -X POST http://localhost:8081/audit/full \
  -H "Content-Type: application/json" \
  -d '{"orgCode":"ORG001"}'

# 查询疑点结果
curl http://localhost:8081/audit/clues/ORG001

# AI 增强全场景稽核
curl -X POST http://localhost:8081/procurement/audit/all/ORG001
```
