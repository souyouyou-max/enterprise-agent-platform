# aip-scheduler

> 定时调度模块：按计划触发数据同步和稽核任务，端口 `8085`。

## 模块职责

- 按配置的机构列表定时执行稽核流程。
- 触发数据接入与规则扫描。
- 输出调度统计和异常日志，作为运营观察入口。

## 核心组件

- `AipSchedulerApplication`
- `AuditEngineScheduler`

## 配置项（示例）

```yaml
server:
  port: 8085
aip:
  audit:
    org-codes: ORG001,ORG002
    interval-seconds: 3600
```

## 启动与验证

```bash
cd /Users/songyangyang/Desktop/enterprise-agent-platform
mvn -pl aip-scheduler -am -DskipTests spring-boot:run

# 调度信息（如果开启 actuator）
curl http://localhost:8085/actuator/health
```
