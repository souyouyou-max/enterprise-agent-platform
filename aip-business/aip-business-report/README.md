# aip-business-report

> 报告生成业务模块，通过 CommunicatorAgent 将 Agent 分析结果转化为三种风格的结构化 Markdown 报告。

## 职责

- 实现 `CommunicatorAgent` 作为 Agent 流水线的最后一环
- 根据 `ReportStyle` 生成差异化格式的报告
- 为报告注入任务元数据头部（taskId/任务名/时间戳/风格/评审分）
- 提供报告管理和分发接口

## 包含内容

### CommunicatorAgent（`com.sinosig.aip.business.report`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.COMMUNICATOR` |
| 输出格式 | 结构化 Markdown，含元数据头部 |

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | 读取 context 中的执行结果和评审反馈，生成最终报告 |

### 三种报告风格

| 风格 | 枚举值 | 格式说明 |
|------|--------|---------|
| 邮件 | `ReportStyle.EMAIL` | 主题行 + 正文 + 落款，适合直接发送给管理层 |
| 摘要 | `ReportStyle.SUMMARY` | 3-5 项关键发现 + 2-3 条建议，500字内，适合快速汇报 |
| 详细 | `ReportStyle.DETAILED` | 完整五章结构：摘要 / 背景 / 发现 / 结论 / 建议 + 附录 |

### 报告元数据头部

每份报告开头自动注入：

```markdown
---
任务ID: 42
任务名称: ORG001 采购合规审查
生成时间: 2026-03-14T10:30:00
报告风格: DETAILED
审查评分: 85/100
---
```

### CommunicatorToolkit（`com.sinosig.aip.business.report.toolkit`）

支持报告格式化和模板管理。

### RiskManagementController（`com.sinosig.aip.business.report.controller`）

提供报告管理和分发接口（查询/下载/推送）。

## 依赖关系

- 依赖：`aip-common`、`aip-engine-agent-core`、`aip-engine-llm`
- 被依赖：`aip-app`（与 `aip-business-chat` 等模块共同参与 `AgentOrchestrator` 流水线）

## 快速使用

```java
// 在 AgentContext 中指定报告风格
AgentContext ctx = AgentContext.builder()
    .taskId(42L)
    .taskName("ORG001 采购合规审查")
    .goal("...")
    .reportStyle(ReportStyle.SUMMARY)   // EMAIL | SUMMARY | DETAILED
    .reviewScore(85)
    .executionResults(executorResults)
    .build();

// CommunicatorAgent 由 AgentOrchestrator 在流水线末端自动调用
// 也可以直接分发调用
AgentResult result = dispatcher.dispatch(AgentRole.COMMUNICATOR, ctx);
String report = result.getOutput(); // Markdown 报告
```

### 报告风格配置示例

```bash
# 指定摘要风格的任务请求
curl -X POST http://localhost:8081/agent/tasks \
  -H "Content-Type: application/json" \
  -d '{"taskName":"快速审查","goal":"...","reportStyle":"SUMMARY"}'
```
