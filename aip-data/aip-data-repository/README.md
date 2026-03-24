# aip-data-repository

> 数据仓库子模块，包含所有业务实体类、MyBatis Mapper 接口、数据服务和工具注册表。

## 职责

- 定义所有数据库实体（MyBatis Plus `@TableName`）
- 提供 MyBatis Mapper 接口（含复杂稽核 SQL）
- 封装 `AgentTaskDataService` 提供任务 CRUD 服务
- 维护 `ToolRegistry` 工具注册表，供 ExecutorAgent 按名调用工具

## 包含内容

### 实体类（`com.sinosig.aip.data.entity`）

| 实体 | 表名 | 说明 |
|------|------|------|
| `AgentTask` | `agent_task` | Agent 任务主表（状态/计划/执行/评审/报告） |
| `AgentSubTask` | `agent_sub_task` | 子任务表（序号/工具/参数/结果/状态） |
| `ClueResult` | `clue_result` | 规则引擎输出的疑点结果 |
| `ProcurementProject` | `procurement_project` | 招采项目 |
| `ProcurementBid` | `procurement_bid` | 投标记录 |
| `ProcurementContract` | `procurement_contract` | 合同记录 |
| `PaymentRecord` | `payment_record` | 付款记录 |
| `SupplierInfo` | `supplier_info` | 供应商信息（含股东 JSON） |
| `SupplierRelation` | `supplier_relation` | 供应商关联关系 |
| `InternalEmployee` | `internal_employee` | 内部员工信息 |
| `ComplianceRecord` | `compliance_record` | 合规记录 |
| `FinanceRecord` | `finance_record` | 财务记录 |

### Mapper 接口（`com.sinosig.aip.data.mapper`）

| Mapper | 关键方法 |
|--------|---------|
| `AgentTaskMapper` | `findPage(offset, size)` / `countAll()` |
| `AgentSubTaskMapper` | `findByTaskId(taskId)` / `deleteByTaskId(taskId)` |
| `ClueResultMapper` | `findByOrgCode(orgCode)` / `findPendingByOrgCode(orgCode)` / `deletePendingByOrgCode(orgCode)` |
| `ClueQueryMapper` | 核心稽核 SQL（见下表） |
| `PaymentRecordMapper` | 付款记录 CRUD |
| `SupplierInfoMapper` | 供应商信息 CRUD |
| `ProcurementProjectMapper` | 招采项目 CRUD |

#### ClueQueryMapper 核心 SQL

| 方法 | SQL 逻辑 |
|------|---------|
| `findUntenderedPayments(orgCode, threshold)` | LEFT JOIN payment_record + procurement_project，WHERE pp.id IS NULL（无招标记录） |
| `findSplitPurchases(orgCode, singleThreshold, totalThreshold)` | GROUP BY supplier，HAVING COUNT≥2 AND SUM>阈值 AND 每笔<阈值（60天窗口） |
| `findCollusiveBids(orgCode)` | procurement_bid 自连接 + supplier_info，匹配相同 legal_person 的不同供应商在同一项目投标 |
| `findLegalPersonConflicts(orgCode)` | supplier_info.legal_person = internal_employee.employee_name |
| `findShareholderConflicts(orgCode)` | supplier_info.shareholders JSON 包含内部员工姓名 |

### 数据服务（`com.sinosig.aip.data.service`）

**`AgentTaskDataService`**

| 方法 | 说明 |
|------|------|
| `createTask(taskName, goal)` | 创建任务，初始状态 PENDING |
| `getById(id)` | 按 ID 查询任务 |
| `getPage(page, size)` | 分页查询任务列表 |
| `updateStatus(taskId, status)` | 更新任务状态 |
| `updateTaskResult(...)` | 更新任务各阶段结果 |
| `saveSubTasks(taskId, subTasks)` | 批量保存子任务 |
| `getSubTasks(taskId)` | 查询任务的子任务列表 |

### 工具注册表（`com.sinosig.aip.tools`）

| 类/接口 | 说明 |
|---------|------|
| `EnterpriseTool` | 工具接口，`execute(params)` 方法 |
| `ToolRegistry` | 工具注册表，自动扫描 `@Component` bean，按名称获取工具 |
| `SalesDataTool` | 按部门/季度查询销售数据 |
| `SqlGeneratorTool` | 自然语言生成 SQL |
| `CrmTool` | CRM 客户数据查询 |
| `EmployeeTool` | 员工信息查询 |

## 依赖关系

- 依赖：`aip-common`
- 被依赖：`aip-data-ingestion`、`aip-engine-rule`、`aip-business-*`、`aip-app`

## 快速使用

```java
// 任务 CRUD
@Autowired AgentTaskDataService taskDataService;
AgentTask task = taskDataService.createTask("供应商审查", "分析 ORG001 的采购合规情况");
taskDataService.updateStatus(task.getId(), TaskStatus.PLANNING);

// 工具调用
@Autowired ToolRegistry toolRegistry;
EnterpriseTool tool = toolRegistry.getTool("getSalesData");
String result = tool.execute(Map.of("department", "采购部", "quarter", "2024Q1"));
```
