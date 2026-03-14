# eap-engine-rule

> 规则引擎模块，内置四条招采稽核规则，通过 SQL 扫描数据仓库中的合规疑点并生成 `ClueResult`。

## 职责

- 定义 `AuditRule` 规则接口
- 实现四条稽核规则（大额未招标/化整为零/围标串标/利益冲突）
- 每条规则独立执行，输出标准化的 `ClueResult` 疑点记录
- 支持按机构编码（orgCode）运行，便于多机构并行扫描

## 包含内容

### 规则接口（`com.enterprise.agent.engine.rule`）

```java
public interface AuditRule {
    String getRuleName();                       // 规则名称
    String getClueType();                       // 疑点类型（枚举值）
    String getRiskLevel();                      // 风险等级（HIGH/MEDIUM/LOW）
    List<ClueResult> execute(String orgCode);   // 执行规则，返回疑点列表
}
```

### 四条规则实现（`com.enterprise.agent.engine.rule.impl`）

#### 1. UntenderedRule — 大额未招标规则

| 属性 | 值 |
|------|----|
| 规则名称 | 大额未招标规则 |
| 疑点类型 | `UNTENDERED` |
| 风险等级 | HIGH |
| 阈值 | 单笔付款 > 50 万元 |
| SQL 逻辑 | `payment_record LEFT JOIN procurement_project`，`WHERE pp.id IS NULL`（无对应采购项目） |

#### 2. SplitPurchaseRule — 化整为零规则

| 属性 | 值 |
|------|----|
| 规则名称 | 化整为零规则 |
| 疑点类型 | `SPLIT_PURCHASE` |
| 风险等级 | HIGH |
| 检测逻辑 | 同一供应商 60 天内：每笔 < 50 万，合计 > 50 万，笔数 ≥ 2 |
| SQL 逻辑 | `GROUP BY supplier_id HAVING COUNT(*) >= 2 AND SUM > 500000 AND MAX < 500000` |

#### 3. CollusiveBidRule — 围标串标规则

| 属性 | 值 |
|------|----|
| 规则名称 | 围标串标规则 |
| 疑点类型 | `COLLUSIVE_BID` |
| 风险等级 | HIGH |
| 检测逻辑 | 同一项目中，不同供应商法定代表人相同 |
| SQL 逻辑 | `procurement_bid` 自连接 + `supplier_info` 匹配，`si1.legal_person = si2.legal_person` |

#### 4. ConflictOfInterestRule — 利益冲突规则

| 属性 | 值 |
|------|----|
| 规则名称 | 利益冲突规则 |
| 疑点类型 | `CONFLICT_OF_INTEREST` |
| 风险等级 | HIGH（法人匹配）/ MEDIUM（股东匹配） |
| 场景 A | `supplier_info.legal_person = internal_employee.employee_name` |
| 场景 B | `supplier_info.shareholders`（JSON）包含内部员工姓名 |
| 去重 | 跨场景去重，防止同一条记录重复计入 |

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`（`ClueQueryMapper`、`ClueResultMapper`）
- 被依赖：`eap-business-screening`（`AuditEngineService`）、`eap-scheduler`

## 扩展新规则

1. 实现 `AuditRule` 接口，添加 `@Component` 注解
2. 在 `getRuleName()` / `getClueType()` / `getRiskLevel()` 中返回规则元数据
3. 在 `execute(orgCode)` 中编写 SQL 逻辑并返回 `List<ClueResult>`
4. 框架自动发现并注入新规则（无需修改 `AuditEngineService`）

```java
@Component
public class AbnormalFrequencyRule implements AuditRule {
    @Override public String getRuleName() { return "异常频次规则"; }
    @Override public String getClueType() { return "ABNORMAL_FREQUENCY"; }
    @Override public String getRiskLevel() { return "MEDIUM"; }

    @Override
    public List<ClueResult> execute(String orgCode) {
        // 编写检测 SQL 逻辑
    }
}
```

## 快速使用

```java
@Autowired List<AuditRule> rules;

// 执行所有规则
rules.forEach(rule -> {
    List<ClueResult> clues = rule.execute("ORG001");
    log.info("规则[{}]发现 {} 条疑点", rule.getRuleName(), clues.size());
});
```
