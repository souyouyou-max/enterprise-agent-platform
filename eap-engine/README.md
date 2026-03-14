# eap-engine

> 引擎层父模块，提供 LLM、RAG、规则引擎和 Agent 四大核心能力，是平台的智能推理中枢。

## 职责

- 聚合四个引擎子模块：LLM 服务、RAG 知识问答、规则引擎、Agent 框架
- 统一管理引擎层依赖和版本
- 为业务层提供标准化的 AI 能力接口

## 子模块分工

| 子模块 | 职责 |
|--------|------|
| `eap-engine-llm` | 多模型 LLM 调用（OpenAI/Claude/Ollama）+ Redis 缓存 |
| `eap-engine-rag` | RAG 知识检索问答 + NL2BI 数据洞察（NL→SQL→分析） |
| `eap-engine-rule` | 四条稽核规则引擎，扫描招采/财务疑点并生成 `ClueResult` |
| `eap-engine-agent` | Agent 框架父模块，含核心抽象 + 三个专业 Agent |

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`
- 被依赖：`eap-business-*`、`eap-app`

## 包含内容

详见各子模块 README：
- [eap-engine-llm/README.md](eap-engine-llm/README.md)
- [eap-engine-rag/README.md](eap-engine-rag/README.md)
- [eap-engine-rule/README.md](eap-engine-rule/README.md)
- [eap-engine-agent/README.md](eap-engine-agent/README.md)
