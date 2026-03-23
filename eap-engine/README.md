# eap-engine

> 引擎层父模块，提供 LLM、RAG、规则引擎、Agent 框架和企业工具集五大核心能力，是平台的智能推理中枢。

## 职责

- 聚合五个引擎子模块：LLM 服务、RAG 知识问答、规则引擎、Agent 框架核心、企业工具集
- 统一管理引擎层依赖和版本
- 为业务层提供标准化的 AI 能力接口

## 子模块分工

| 子模块 | 职责 |
|--------|------|
| `eap-engine-llm` | 多模型 LLM 调用（OpenAI/Claude/Ollama）+ `@Cacheable` 接入（CacheManager 由 eap-app 提供） |
| `eap-engine-rag` | RAG 知识检索问答 + NL2BI 数据洞察（NL→SQL→分析） |
| `eap-engine-rule` | 四条稽核规则引擎，扫描招采/财务疑点并生成 `ClueResult` |
| `eap-engine-agent-core` | Agent 框架核心：`BaseAgent` 抽象、`AgentContext`、`AgentResult`、`AgentOrchestrator` |
| `eap-engine-tools` | 企业工具集：`EnterpriseTool` 接口、`ToolRegistry`，提供 CRM/员工/销售/OCR/SQL 等七类 `@Tool` 实现 |

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`
- 被依赖：`eap-business-*`、`eap-app`

## 包含内容

详见各子模块 README：
- [eap-engine-llm/README.md](eap-engine-llm/README.md)
- [eap-engine-rag/README.md](eap-engine-rag/README.md)
- [eap-engine-rule/README.md](eap-engine-rule/README.md)
- [eap-engine-agent-core/README.md](eap-engine-agent-core/README.md)
- [eap-engine-tools/README.md](eap-engine-tools/README.md)
