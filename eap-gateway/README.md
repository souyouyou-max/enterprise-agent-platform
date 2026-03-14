# eap-gateway

> API 网关模块，基于 Spring Cloud Gateway，统一对外暴露服务入口，路由到后端应用。

## 职责

- 作为系统唯一对外入口，监听端口 **8080**
- 将外部请求路由到 `eap-app`（lb://eap-app，负载均衡）
- 提供统一的 CORS、限流、鉴权扩展点

## 包含内容

### 应用入口

- `EapGatewayApplication`：Spring Cloud Gateway 启动类

### 路由配置

所有路由均指向 `lb://eap-app`（Spring Cloud LoadBalancer 负载均衡）：

| 路由 ID | 匹配路径 | 目标服务 |
|---------|---------|---------|
| `agent-tasks` | `/agent/tasks/**` | lb://eap-app |
| `audit` | `/audit/**` | lb://eap-app |
| `procurement-audit` | `/procurement/audit/**` | lb://eap-app |
| `chat` | `/chat/**` | lb://eap-app |
| `knowledge` | `/knowledge/**` | lb://eap-app |
| `insight` | `/insight/**` | lb://eap-app |

> 路由规则在 `application.yml` 的 `spring.cloud.gateway.routes` 中配置。

### CORS 说明

网关层统一配置跨域，允许前端开发环境访问：

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods: GET,POST,PUT,DELETE,OPTIONS
            allowedHeaders: "*"
```

## 依赖关系

- 依赖：Spring Cloud Gateway、Spring Cloud LoadBalancer
- 路由目标：`eap-app`（:8081）
- 可选：Nacos 服务注册（默认禁用）

## 快速使用

### 启动

```bash
cd eap-gateway
mvn spring-boot:run
# 网关监听 http://localhost:8080
```

### 通过网关访问服务

```bash
# 通过网关（8080）访问任务接口
curl -X POST http://localhost:8080/agent/tasks \
  -H "Content-Type: application/json" \
  -d '{"taskName":"测试任务","goal":"分析采购数据"}'

# 通过网关访问对话接口
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s001","message":"你好"}'
```

### 扩展路由

在 `application.yml` 中添加新路由：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: new-service
          uri: lb://eap-app
          predicates:
            - Path=/new-path/**
```
