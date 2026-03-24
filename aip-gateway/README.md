# aip-gateway

> 统一入口网关模块（Spring Cloud Gateway），当前端口 `8079`。

## 模块职责

- 提供统一路由、CORS、限流、熔断。
- 将前端请求统一转发至 `aip-app` 与 Python 分析服务。
- 屏蔽下游服务细节，稳定对外入口地址。

## 关键说明

- 当前使用 Spring Cloud Gateway 5 配置前缀：`spring.cloud.gateway.server.webflux.*`。
- 本地开发默认建议关闭 Nacos 配置覆盖，避免路由被远端配置替换。

## 常用验证

```bash
# 网关健康
curl http://localhost:8079/actuator/health

# 已加载路由数（应大于 0）
curl http://localhost:8079/actuator/metrics/spring.cloud.gateway.routes.count

# 典型业务路径
curl -X POST http://localhost:8079/api/v1/chat/session
```

## 启动

```bash
cd /Users/songyangyang/Desktop/enterprise-agent-platform
JAVA_HOME=/Users/songyangyang/Library/Java/JavaVirtualMachines/corretto-19.0.2/Contents/Home \
NACOS_CONFIG_ENABLED=false NACOS_ENABLED=false \
mvn -pl aip-gateway spring-boot:run
```

> 提示：当前 `docker-compose.yml` 仅负责基础设施（PostgreSQL/Redis/Nacos），网关与业务应用通过 Maven 本地启动。
