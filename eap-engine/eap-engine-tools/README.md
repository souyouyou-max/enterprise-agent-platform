# eap-engine-tools

> 企业工具集模块，提供可供 Spring AI `@Tool` 调用的标准化企业数据接入工具，涵盖 CRM、员工、销售、OCR、SQL 生成等能力。

## 职责

- 定义 `EnterpriseTool` 工具接口和 `ToolRegistry` 注册表
- 实现七类企业数据工具，供 Agent 通过 `@Tool` 机制直接调用
- 封装 HTTP 客户端配置（`HttpClientConfig`），统一管理外部 API 连接池

## 包含内容

### 工具接口与注册表

#### EnterpriseTool（`com.enterprise.agent.tools`）

所有工具的标准接口：

```java
public interface EnterpriseTool {
    String getName();         // 工具唯一名称（用于 ToolRegistry 检索）
    String getDescription();  // 工具描述（LLM 用于决策调用时机）
}
```

#### ToolRegistry（`com.enterprise.agent.tools`）

工具注册表，持有所有 `EnterpriseTool` Bean，提供按名称检索能力，供 Agent 动态获取工具列表。

### 七类工具实现（`com.enterprise.agent.tools.impl`）

| 工具类 | 名称 | 能力描述 |
|--------|------|----------|
| `CrmTool` | crm | 查询合同、往来记录等 CRM 数据 |
| `EmployeeTool` | employee | 查询员工信息、组织关系 |
| `SalesDataTool` | salesData | 查询销售数据、业绩指标 |
| `SqlGeneratorTool` | sqlGenerator | 将自然语言转为 SQL 查询语句 |
| `DazhiOcrTool` | dazhiOcr | 大智部通用 OCR：图片/文件文字识别 |
| `ZhengyanPlatformTool` | zhengyanPlatform | 正言多模态平台：img2text 图文理解 |
| `ZhengyanTextClassificationTool` | zhengyanClassify | 正言文本分类：结构化信息抽取 |

### 基础设施

#### HttpClientConfig（`com.enterprise.agent.tools.config`）

配置 `RestTemplate`，统一管理外部 API 调用的连接池参数：连接超时、读取超时、最大连接数。

## 依赖关系

- 依赖：`eap-common`（`LlmService`、`ToolResponse`）、`spring-web`（RestTemplate）
- 被依赖：`eap-business-task`、`eap-business-chat`、`eap-business-pipeline`（MultimodalServiceImpl）

## 扩展新工具

1. 实现 `EnterpriseTool` 接口，添加 `@Component` 注解
2. 在业务类中添加 `@Tool` 方法，调用该工具
3. `ToolRegistry` 自动注入所有 `EnterpriseTool` Bean，无需手动注册

```java
@Component
public class FinanceTool implements EnterpriseTool {
    @Override public String getName() { return "finance"; }
    @Override public String getDescription() { return "查询财务报表和预算执行数据"; }

    @Tool(description = "查询指定机构的财务数据")
    public ToolResponse queryFinance(String orgCode, String period) {
        // 调用财务系统 API
    }
}
```
