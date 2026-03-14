# eap-engine-agent-monitor

> 监测预警 Agent，对机构各项指标进行阈值检查，并按严重程度生成四级预警通知。

## 职责

- 实时检查机构运营/合规/采购/财务/合同指标是否超过预设阈值
- 按超阈值幅度生成四级预警（RED/ORANGE/YELLOW/GREEN）
- 提供预警规则管理（12条活跃规则：4采购+4财务+4合同）
- 生成包含整改建议的预警通知文本

## 包含内容

### MonitoringAgent（`com.enterprise.agent.engine.agent.monitor`）

| 属性 | 值 |
|------|----|
| 继承 | `BaseAgent` |
| 角色 | `AgentRole.MONITORING` |
| 系统提示词主题 | 实时阈值监控、分级预警触发 |

| 方法 | 说明 |
|------|------|
| `execute(AgentContext)` | 标准 Agent 接口，执行监测预警分析 |
| `monitorOrg(orgCode)` | 对机构执行全量监测并输出预警报告 |
| `checkThresholds(orgCode)` | 仅检查阈值，返回预警发现列表 |

### MonitoringToolkit（`com.enterprise.agent.engine.agent.monitor.toolkit`）

Spring AI `@Tool` 注解工具集：

| 工具方法 | 输入 | 输出 |
|---------|------|------|
| `checkRiskThresholds(orgCode)` | 机构编码 | 预警发现列表（含超阈值百分比） |
| `getDashboardData(orgCode)` | 机构编码 | 各级预警数量、趋势方向、最后更新时间 |
| `getAlertRules(orgCode, category)` | 机构编码 + 分类 | 当前活跃的预警规则列表（共12条） |
| `generateAlertNotification(orgCode, alertLevel)` | 机构编码 + 预警级别 | 带整改建议的预警通知文本 |

### 预警级别说明

| 级别 | 颜色 | 触发条件 | 响应要求 |
|------|------|---------|---------|
| RED | 红色 | 指标超阈值 > 50% | 立即处置，上报管理层 |
| ORANGE | 橙色 | 指标超阈值 20%-50% | 48 小时内处置 |
| YELLOW | 黄色 | 指标超阈值 0%-20% | 一周内跟进处置 |
| GREEN | 绿色 | 指标正常 | 常规监控 |

### 活跃预警规则分布

| 分类 | 规则数 | 示例 |
|------|--------|------|
| 采购类 | 4 | 无标采购比例、供应商集中度、合同超期 |
| 财务类 | 4 | 费用超预算、报销异常频次、大额支出 |
| 合同类 | 4 | 提前付款比例、签署授权偏差、履约期 |

## 依赖关系

- 依赖：`eap-engine-agent-core`、`eap-engine-llm`
- 被依赖：`eap-business-screening`、`eap-app`

## 快速使用

```java
@Autowired AgentDispatcher dispatcher;

AgentContext ctx = AgentContext.builder()
    .goal("监测机构 ORG001 当前风险预警状态")
    .metadata(Map.of("orgCode", "ORG001"))
    .build();

AgentResult result = dispatcher.dispatch(AgentRole.MONITORING, ctx);
// result.getOutput() 包含：预警汇总、各级预警详情、整改建议
```
