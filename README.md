# Enterprise Agent Platform

企业级 Agent 平台，采用 `common -> data -> engine -> business -> app` 分层，配套 `gateway` 与 `scheduler`。

## 目录结构

```text
enterprise-agent-platform
├── aip-common
├── aip-data
│   ├── aip-data-repository
│   └── aip-data-ingestion
├── aip-engine
│   ├── aip-engine-llm
│   ├── aip-engine-agent-core
│   ├── aip-engine-rag
│   ├── aip-engine-rule
│   └── aip-engine-tools
├── aip-business
│   ├── aip-business-screening
│   ├── aip-business-report
│   ├── aip-business-auditing
│   ├── aip-business-pipeline
│   └── aip-business-chat
├── aip-app
├── aip-gateway
└── aip-scheduler
```

## 运行端口

- `aip-app`: `8081`
- `aip-gateway`: `8079`
- `aip-scheduler`: `8085`
- `bid-analysis-service`（Python）: `8099`

## 快速开始

```bash
cd /Users/songyangyang/Desktop/enterprise-agent-platform

# 1) 启动基础设施（PostgreSQL/Redis/Nacos）
docker compose up -d

# 2) 编译
mvn -DskipTests compile

# 3) 启动主应用
mvn -pl aip-app -am -DskipTests spring-boot:run

# 4) 可选：启动网关与调度器
mvn -pl aip-gateway -DskipTests spring-boot:run
mvn -pl aip-scheduler -am -DskipTests spring-boot:run
```

## 一键开发命令

```bash
# 查看命令
make help

# 启动基础设施
make infra-up

# 编译主应用及依赖
make compile

# 分别启动应用（建议开 3 个终端）
make app
make gateway
make scheduler

# 健康检查
make health

# 全量编译检查（含 aip-gateway/aip-scheduler）
make verify

# 扫描旧命名残留（排除 logs/node_modules/dist）
make scan-legacy
```

## 模块文档索引

- [aip-common/README.md](aip-common/README.md)
- [aip-data/README.md](aip-data/README.md)
- [aip-engine/README.md](aip-engine/README.md)
- [aip-business/README.md](aip-business/README.md)
- [aip-app/README.md](aip-app/README.md)
- [aip-gateway/README.md](aip-gateway/README.md)
- [aip-scheduler/README.md](aip-scheduler/README.md)
