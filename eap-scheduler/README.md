# eap-scheduler

> 定时调度模块，监听端口 **8085**，负责自动化的数据同步和规则扫描定时任务。

## 职责

- 定时触发数据同步（从四个外部系统拉取最新数据）
- 定时执行稽核规则扫描（对所有配置的机构运行四条规则）
- 记录每次调度的疑点统计结果（按 HIGH/MEDIUM/LOW 分类）
- 支持通过属性配置调度机构列表和时间间隔

## 包含内容

### 应用入口

- `EapSchedulerApplication`：Spring Boot 启动类（端口 8085）

### AuditEngineScheduler（`com.enterprise.agent.scheduler`）

| 属性 | 说明 |
|------|------|
| 注解 | `@Component` |
| 依赖 | `AuditEngineService`、`List<DataSourceAdapter>` |

#### 定时任务

| 方法 | 默认配置 | 生产建议 |
|------|---------|---------|
| `scheduledFullAudit()` | `fixedDelay=3600s`，`initialDelay=30s`（每小时） | `cron: "0 0 3 * * ?"` （凌晨3点） |

> 数据同步默认与规则扫描合并在 `fullAudit()` 中执行。如需分开，可单独调度 `syncAllDataSources()`（建议凌晨2点）和 `runAllRules()`（凌晨3点）。

#### 主要方法

| 方法 | 说明 |
|------|------|
| `scheduledFullAudit()` | 定时入口：遍历所有机构，依次执行 `fullAudit()` |
| `runAuditForOrg(orgCode)` | 对单个机构执行完整稽核（数据同步 + 规则扫描） |
| `summarize(orgCode, clues)` | 日志输出疑点统计（HIGH/MEDIUM/LOW 数量） |

### 调度流程

```
定时触发（每小时 or 凌晨3点）
  ↓
遍历 eap.audit.org-codes（默认: ORG001）
  ↓ 对每个机构：
  AuditEngineService.fullAudit(orgCode)
    ├─ syncAllDataSources()   ← 4个适配器数据同步
    └─ runAllRules(orgCode)   ← 4条规则扫描 + 持久化 ClueResult
  ↓
summarize(): 输出统计日志
```

## 依赖关系

- 依赖：`eap-common`、`eap-data-repository`、`eap-data-ingestion`、`eap-engine-rule`、`eap-business-screening`

## 配置说明

```yaml
# application.yml
server:
  port: 8085

eap:
  audit:
    org-codes: ORG001,ORG002   # 参与稽核的机构列表（逗号分隔）
    interval-seconds: 3600      # fixedDelay 调度间隔（秒）

# 生产环境建议改为 cron 表达式
# spring.task.scheduling.pool.size: 5
```

## 手动触发

调度模块部署后可通过 `AuditEngineController`（在 `eap-app` 上）手动触发：

```bash
# 手动触发完整稽核（等效于调度器的一次执行）
curl -X POST http://localhost:8081/audit/full \
  -H "Content-Type: application/json" \
  -d '{"orgCode":"ORG001"}'
```

或直接 HTTP 调用调度器（如暴露了 Actuator）：

```bash
# 查看调度任务状态
curl http://localhost:8085/actuator/scheduledtasks
```
