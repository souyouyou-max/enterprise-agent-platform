# aip-data-ingestion

> 数据接入子模块，通过适配器模式对接四个外部业务系统，将数据同步至统一数据仓库。

## 职责

- 定义 `DataSourceAdapter` 适配器接口
- 实现四个外部系统适配器（招采/费控/EHR/企查查）
- 将外部数据转换为标准实体并写入数据库，供规则引擎扫描

## 包含内容

### 适配器接口（`com.sinosig.aip.data.adapter`）

```java
public interface DataSourceAdapter {
    String getSourceName();  // 数据源名称
    void syncData();         // 执行数据同步
}
```

### 四个适配器实现（`com.sinosig.aip.data.adapter.impl`）

| 适配器 | 数据源 | 写入表 | 说明 |
|--------|--------|--------|------|
| `FeikongSystemAdapter` | 费控系统 | `payment_record` | 付款记录，含大额未招标/化整为零场景数据（8条） |
| `QichachaAdapter` | 企查查 | `supplier_info` | 供应商工商信息，含法人/股东（JSON）/注册信息 |
| `EhrSystemAdapter` | HR系统 | `internal_employee` | 内部员工：姓名/部门/岗位/机构编码 |
| `ZhaocaiSystemAdapter` | 招采系统 | `procurement_project` + `procurement_bid` | 招标项目及投标记录 |

### 数据同步流程

```
外部系统（Mock 实现）
     ↓
DataSourceAdapter.syncData()
     ↓
数据清洗/转换（字段映射）
     ↓
MyBatis Mapper 批量写入
     ↓
PostgreSQL（aip_db）
```

### Mock 数据说明（FeikongSystemAdapter）

| 记录 | 场景 |
|------|------|
| PAY-001/002 | 正常付款（有招标记录） |
| PAY-003 | 大额未招标（>50万无采购项目） |
| PAY-004/005/006 | 化整为零（SUP004 同一供应商 60天内3笔，每笔<50万，合计>50万） |
| PAY-007/008 | 化整为零（SUP005 同一供应商 60天内2笔，每笔<50万，合计>50万） |

## 依赖关系

- 依赖：`aip-common`、`aip-data-repository`
- 被依赖：`aip-business-screening`（`AuditEngineService`）、`aip-scheduler`

## 快速使用

```java
// 注入全部适配器，批量同步
@Autowired List<DataSourceAdapter> adapters;

adapters.forEach(adapter -> {
    log.info("同步数据源: {}", adapter.getSourceName());
    adapter.syncData();
});
```

> 扩展新数据源：实现 `DataSourceAdapter` 接口并注册为 `@Component`，无需修改调用方。
