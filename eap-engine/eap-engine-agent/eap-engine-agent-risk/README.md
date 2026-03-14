# eap-engine-agent-risk

> 风险透视 Agent，对机构进行多维度风险评分（0-100），生成综合风险分析报告。

## 职责

- 从运营、合规、财务、采购四个维度对机构进行量化风险评分
- 汇总综合风险得分，映射为风险等级（绿/黄/橙/红）
- 获取风险分布（按业务线）和历史审计记录
- 输出包含评分矩阵和改善建议的结构化报告

## 包含内容

### RiskAnalysisAgent（`com.enterprise.agent.engine.agent.risk`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.RISK_ANALYSIS` |
| 系统提示词主题 | 多维风险画像（运营/合规/财务/采购）、综合评分 |

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | 标准 Agent 接口，执行综合风险分析 |
| `analyzeOrgRisk(orgCode)` | 对指定机构执行全维度风险分析 |

### RiskAnalysisToolkit（`com.enterprise.agent.engine.agent.risk.toolkit`）

Spring AI `@Tool` 注解工具集：

| 工具方法 | 输入 | 输出 |
|---------|------|------|
| `getOperationalMetrics(orgCode)` | 机构编码 | 运营指标（保费收入/赔付率/综合成本率/同比增长） |
| `calculateRiskScore(orgCode)` | 机构编码 | 四维评分 + 综合得分 + 风险等级 |
| `getRiskDistribution(orgCode)` | 机构编码 | 各业务线（采购/财务/合同/人员）风险分布 |
| `getHistoricalAuditRecords(orgCode)` | 机构编码 | 历史审计记录及整改情况 |
| `generateRiskReport(orgCode, includeRecommendations)` | 机构编码 + 是否含建议 | 带评分矩阵和改善建议的 Markdown 报告 |

### 风险等级映射

| 综合得分 | 风险等级 | 颜色标识 |
|---------|---------|---------|
| 90-100 | 优秀 | 绿色 |
| 70-89 | 良好 | 黄色 |
| 50-69 | 中等 | 橙色 |
| 0-49 | 危险 | 红色 |

### 评分维度权重

| 维度 | 说明 |
|------|------|
| 运营风险 | 保费规模、赔付率、成本率等运营指标 |
| 合规风险 | 违规记录、整改完成率、审计发现 |
| 财务风险 | 资金流、预算执行偏差、异常交易 |
| 采购风险 | 招标合规率、供应商集中度、利益冲突 |

## 依赖关系

- 依赖：`eap-engine-agent-core`、`eap-engine-llm`
- 被依赖：`eap-business-screening`、`eap-app`

## 快速使用

```java
@Autowired AgentDispatcher dispatcher;

AgentContext ctx = AgentContext.builder()
    .goal("对机构 ORG001 进行全面风险评估")
    .reportStyle(ReportStyle.DETAILED)
    .metadata(Map.of("orgCode", "ORG001"))
    .build();

AgentResult result = dispatcher.dispatch(AgentRole.RISK_ANALYSIS, ctx);
// result.getOutput() 包含：综合得分、四维评分、风险分布、改善建议
```
