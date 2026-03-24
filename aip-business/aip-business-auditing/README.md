# aip-business-auditing

> 稽核智能体模块，实现线索发现、风险透视、监测预警三个专业 Agent，并对外暴露机构风险远程管理 REST API。

## 职责

- 实现三个稽核智能体，每个 Agent 持有专属 Toolkit（`@Tool` 方法集）
- 通过 `RiskManagementController` 对外暴露四个 REST 接口
- 统一走 `BaseAgent.execute()` 链路，与 `AgentOrchestrationToolkit` / `AgentOrchestrator` 解耦

## 三个稽核 Agent

### ClueDiscoveryAgent — 线索发现

| 属性 | 值 |
|------|----|
| 角色 | `AgentRole.CLUE_DISCOVERY` |
| Toolkit | `ClueDiscoveryToolkit` |
| 功能 | 扫描采购/财务/合同各主题数据，发现异常疑点线索 |

**主要方法：**

| 方法 | 说明 |
|------|------|
| `scanAll(orgCode)` | 全量扫描三个主题，汇总返回 `AgentResult` |
| `scanByTopic(orgCode, topic)` | 按主题单独扫描：`procurement` / `finance` / `contract` |

`ClueDiscoveryToolkit` 内置三个 `@Tool` 方法，供 LLM 按需调用：
- `scanProcurementClues(orgCode)` — 招采疑点
- `scanFinancialClues(orgCode)` — 财务疑点
- `scanContractClues(orgCode)` — 合同疑点

---

### RiskAnalysisAgent — 风险透视

| 属性 | 值 |
|------|----|
| 角色 | `AgentRole.RISK_ANALYSIS` |
| Toolkit | `RiskAnalysisToolkit` |
| 功能 | 四维度风险评分（经营/合规/财务/采购），生成综合风险画像 |

**主要方法：**

| 方法 | 说明 |
|------|------|
| `analyzeOrgRisk(orgCode)` | 对指定机构做全维度风险分析，返回评分报告 |

`RiskAnalysisToolkit` 内置：
- `getOperationalRisk(orgCode)` — 经营风险指标
- `getComplianceRisk(orgCode)` — 合规风险指标
- `getFinancialRisk(orgCode)` — 财务风险指标
- `getProcurementRisk(orgCode)` — 采购风险指标

---

### MonitoringAgent — 监测预警

| 属性 | 值 |
|------|----|
| 角色 | `AgentRole.MONITORING` |
| Toolkit | `MonitoringToolkit` |
| 功能 | 持续检查各项风险指标阈值，生成分级预警通知（红/橙/黄/绿） |

**主要方法：**

| 方法 | 说明 |
|------|------|
| `monitorOrg(orgCode)` | 检查所有监测指标，返回预警级别和处置建议 |

`MonitoringToolkit` 内置：
- `checkThresholds(orgCode)` — 指标阈值检查
- `getAlertHistory(orgCode)` — 历史预警记录
- `generateAlertReport(orgCode)` — 生成预警通知

## REST API（`/api/v1/risk-management`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/clue/scan?orgCode=&topic=` | 线索发现扫描（topic 可选，不传则全量） |
| POST | `/risk/analyze?orgCode=` | 机构风险透视分析 |
| POST | `/monitoring/check?orgCode=` | 监测预警检查 |
| POST | `/full-audit?orgCode=` | 全量稽核（同时调用三个 Agent） |

### 示例

```bash
# 全量稽核
curl -X POST "http://localhost:8081/api/v1/risk-management/full-audit?orgCode=ORG001"

# 只扫采购线索
curl -X POST "http://localhost:8081/api/v1/risk-management/clue/scan?orgCode=ORG001&topic=procurement"

# 风险透视
curl -X POST "http://localhost:8081/api/v1/risk-management/risk/analyze?orgCode=ORG001"
```

## 依赖关系

- 依赖：`aip-common`（`LlmService`、`AgentRole`、`ResponseResult`）、`aip-engine-agent-core`（`BaseAgent`、`AgentContext`、`AgentResult`）、`spring-ai-starter-model-openai`
- 被依赖：`aip-business-chat`（`AgentOrchestrationToolkit`）、`aip-app`（运行时装配）
