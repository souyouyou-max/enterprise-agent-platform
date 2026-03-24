# aip-engine

> 引擎层聚合模块：提供 LLM、Agent Core、RAG、规则引擎与工具能力。

## 模块职责

- 对业务层输出稳定的 AI 能力接口与基础执行框架。
- 统一管理引擎子模块依赖和版本。
- 保持引擎能力可复用，避免业务实现下沉到引擎层。

## 子模块清单

| 子模块 | 主要能力 |
|---|---|
| `aip-engine-llm` | 模型调用与缓存封装 |
| `aip-engine-agent-core` | Agent 抽象、调度、编排、能力接口 |
| `aip-engine-rag` | 知识检索问答与 NL2BI |
| `aip-engine-rule` | 稽核规则执行与疑点输出 |
| `aip-engine-tools` | 企业工具实现与注册 |

## 依赖边界

- 推荐方向：`business -> engine`，不反向依赖业务模块。
- `agent-core` 不直接依赖 provider starter，改由 `aip-engine-llm` 承接。

## 本地验证

```bash
mvn -pl aip-engine -am -DskipTests compile
```

## 子模块文档索引

- [aip-engine-llm/README.md](aip-engine-llm/README.md)
- [aip-engine-agent-core/README.md](aip-engine-agent-core/README.md)
- [aip-engine-rag/README.md](aip-engine-rag/README.md)
- [aip-engine-rule/README.md](aip-engine-rule/README.md)
- [aip-engine-tools/README.md](aip-engine-tools/README.md)
