# aip-business

> 业务层聚合模块：将引擎层能力编排为面向接口调用的业务能力。

## 模块职责

- 承载业务 Agent 与业务服务，暴露稳定的 REST API。
- 负责业务流程编排（任务流、会话流、OCR 流、稽核流）。
- 保持对引擎层的单向依赖，避免业务模块之间硬耦合。

## 子模块清单

| 子模块 | 主要能力 |
|---|---|
| `aip-business-screening` | 招采规则稽核与疑点检索 |
| `aip-business-report` | 报告生成与风险管理接口 |
| `aip-business-auditing` | 线索发现、风险透视、监测预警 Agent |
| `aip-business-pipeline` | OCR/多模态分析/相似度对比流水线 |
| `aip-business-chat` | 统一对话入口、工具编排与同步 Pipeline（Planner/Executor/Reviewer） |

## 依赖边界（当前约束）

- 业务层禁止直接引入 `spring-ai-starter-model-openai`（由父 POM Enforcer 检查）。
- `aip-business-chat` 禁止直接依赖 `aip-business-auditing`（通过 `AuditingCapability` 接口解耦）。
- 推荐依赖方向：`business -> engine -> data -> common`。

## 本地验证

```bash
# 编译业务层全部子模块
mvn -pl aip-business -am -DskipTests compile

# 编译并验证主应用装配
mvn -pl aip-app -am -DskipTests compile
```

## 子模块文档索引

- [aip-business-screening/README.md](aip-business-screening/README.md)
- [aip-business-report/README.md](aip-business-report/README.md)
- [aip-business-auditing/README.md](aip-business-auditing/README.md)
- [aip-business-pipeline/README.md](aip-business-pipeline/README.md)
- [aip-business-chat/README.md](aip-business-chat/README.md)
