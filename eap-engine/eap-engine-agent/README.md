# eap-engine-agent

> Agent 引擎父模块，提供 Agent 核心抽象框架和三个专业领域 Agent 实现。

## 职责

- 聚合 Agent 框架的四个子模块
- 统一管理 Agent 层依赖
- 定义 Agent 能力边界：核心抽象 + 线索发现 + 风险透视 + 监测预警

## 子模块分工

| 子模块 | Agent 类 | 职责 |
|--------|----------|------|
| `eap-engine-agent-core` | `BaseAgent` / `AgentOrchestrator` / `AgentDispatcher` | 抽象基类、编排器、分发器、上下文模型 |
| `eap-engine-agent-clue` | `ClueDiscoveryAgent` | 扫描采购/财务/合同疑点，输出分级线索报告 |
| `eap-engine-agent-risk` | `RiskAnalysisAgent` | 多维风险评分（0-100）+ 综合风险报告 |
| `eap-engine-agent-monitor` | `MonitoringAgent` | 阈值检查 + 四级预警通知（RED/ORANGE/YELLOW/GREEN） |

## BaseAgent 抽象说明

所有 Agent 均继承 `BaseAgent`，须实现三个抽象方法：

```java
public abstract class BaseAgent {
    protected abstract AgentRole getRole();
    protected abstract String getSystemPrompt();
    protected abstract AgentResult execute(AgentContext context);

    // 工具方法
    protected ChatClient buildChatClient();                          // 注入系统提示词
    protected String callLlmWithRetry(String prompt, int maxRetries); // 指数退避重试
    protected String sanitizeInput(String input);                    // 防止提示词注入
}
```

## 依赖关系

- 依赖：`eap-common`、`eap-engine-llm`
- 被依赖：`eap-business-task`、`eap-business-screening`、`eap-business-report`、`eap-business-chat`

## 包含内容

详见各子模块 README：
- [eap-engine-agent-core/README.md](eap-engine-agent-core/README.md)
- [eap-engine-agent-clue/README.md](eap-engine-agent-clue/README.md)
- [eap-engine-agent-risk/README.md](eap-engine-agent-risk/README.md)
- [eap-engine-agent-monitor/README.md](eap-engine-agent-monitor/README.md)
