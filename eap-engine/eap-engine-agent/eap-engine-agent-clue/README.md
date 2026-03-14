# eap-engine-agent-clue

> 线索发现 Agent，专注于扫描采购、财务和合同领域的合规疑点，输出分级结构化线索报告。

## 职责

- 扫描采购领域疑点（超额付款/无招标记录/供应商集中度异常）
- 扫描财务领域疑点（费用异常/报销违规/账目异常）
- 扫描合同领域疑点（提前付款/签署异常/条款不合规）
- 汇总疑点并按风险等级（HIGH/MEDIUM/LOW）输出 Markdown 线索报告

## 包含内容

### ClueDiscoveryAgent（`com.enterprise.agent.engine.agent.clue`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.CLUE_DISCOVERY` |
| 系统提示词主题 | 招采稽核异常识别、分级风险输出 |

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | 标准 Agent 接口，从 context.goal 中解析 orgCode 并执行 |
| `scanByTopic(orgCode, topic)` | 按指定主题（procurement/finance/contract）扫描 |
| `scanAll(orgCode)` | 执行全量扫描（三个领域） |

### ClueDiscoveryToolkit（`com.enterprise.agent.engine.agent.clue.toolkit`）

Spring AI `@Tool` 注解工具集，供 Agent 在推理过程中调用：

| 工具方法 | 输入 | 输出 |
|---------|------|------|
| `scanProcurementClues(orgCode)` | 机构编码 | 采购领域疑点（超额/无标/集中度） |
| `scanFinanceClues(orgCode)` | 机构编码 | 财务领域疑点（费用异常/报销违规） |
| `scanContractClues(orgCode)` | 机构编码 | 合同领域疑点（提前付款/签署异常） |
| `generateClueReport(orgCode, cluesJson)` | 机构编码 + 疑点 JSON | 结构化 Markdown 报告（按 HIGH/MEDIUM/LOW 分级） |

## 输出示例

```markdown
# 线索发现报告 - ORG001

## 高风险线索（HIGH）
- 【采购】供应商 SUP004 存在化整为零嫌疑，60天内3笔付款合计 ¥1,520,000
- 【采购】付款记录 PAY-003（¥780,000）无对应采购项目

## 中风险线索（MEDIUM）
- 【财务】部门 D002 费用报销频次异常，较上月增加 320%
- 【合同】合同 C-2024-015 提前付款 45 天，超出合同约定

## 低风险线索（LOW）
- 【合同】3份合同签署人与授权清单存在偏差
```

## 依赖关系

- 依赖：`eap-engine-agent-core`、`eap-engine-llm`
- 被依赖：`eap-business-screening`、`eap-app`

## 快速使用

```java
@Autowired AgentDispatcher dispatcher;

AgentContext ctx = AgentContext.builder()
    .goal("扫描机构 ORG001 的采购和财务疑点")
    .metadata(Map.of("orgCode", "ORG001"))
    .build();

AgentResult result = dispatcher.dispatch(AgentRole.CLUE_DISCOVERY, ctx);
System.out.println(result.getOutput()); // Markdown 线索报告
```
