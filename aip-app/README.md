# aip-app

> 主应用装配模块：统一启动入口，端口 `8081`。

## 模块职责

- 装配 `common/data/engine/business` 各层 Bean。
- 提供统一配置、安全、异步、序列化与运行时管理能力。
- 暴露业务 API 与 OpenAPI 文档。

## 关键说明

- `aip-app` 以装配为主，不建议放业务域实现。
- 已去掉对 `aip-data-repository` 的历史 workaround 直依赖。

## 启动方式

```bash
cd /Users/songyangyang/Desktop/enterprise-agent-platform
JAVA_HOME=/Users/songyangyang/Library/Java/JavaVirtualMachines/corretto-19.0.2/Contents/Home \
mvn -pl aip-app -am -DskipTests spring-boot:run
```

## 常用地址

- 应用：`http://localhost:8081`
- Swagger：`http://localhost:8081/swagger-ui.html`
- 健康检查：`http://localhost:8081/actuator/health`

## 回归验证

```bash
# 编译装配链
mvn -pl aip-app -am -DskipTests compile
```
