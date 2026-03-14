# eap-business

> 业务层父模块，将引擎层能力组装为面向用户的业务服务，下辖四个子模块。

## 职责

- 聚合四个业务子模块：任务管理、疑点筛查、报告生成、AI 交互
- 统一管理业务层依赖和版本
- 暴露 REST API，对接前端和外部调用方

## 子模块分工

| 子模块 | 核心 Agent | 职责 |
|--------|-----------|------|
| `eap-business-task` | Planner + Executor + Reviewer | 任务全生命周期管理，Kafka 事件驱动 |
| `eap-business-screening` | ProcurementAuditAgent + AuditEngineService | 招采稽核四大场景，规则引擎触发 |
| `eap-business-report` | CommunicatorAgent | 三种风格报告生成（EMAIL/SUMMARY/DETAILED） |
| `eap-business-chat` | InsightAgent + InteractionCenterAgent | 多轮对话、意图路由、NL2BI |

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`、`eap-engine-*`
- `eap-business-chat` 依赖 `eap-business-screening`（`InteractionCenterAgent` 调用 `ProcurementAuditAgent`）
- 被依赖：`eap-app`

## 包含内容

详见各子模块 README：
- [eap-business-task/README.md](eap-business-task/README.md)
- [eap-business-screening/README.md](eap-business-screening/README.md)
- [eap-business-report/README.md](eap-business-report/README.md)
- [eap-business-chat/README.md](eap-business-chat/README.md)
