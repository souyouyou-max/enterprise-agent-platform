# aip-data

> 数据层父模块，负责统一管理数据仓库访问和外部数据源接入，下辖两个子模块。

## 职责

- 聚合数据层两个子模块：数据仓库（repository）与数据接入（ingestion）
- 统一管理 MyBatis Plus 实体、Mapper 和数据服务
- 提供外部系统数据同步的适配器抽象

## 子模块分工

| 子模块 | 职责 |
|--------|------|
| `aip-data-repository` | 数据库实体/Mapper/工具注册表，提供统一数据访问服务 |
| `aip-data-ingestion` | 外部数据源适配器，负责从招采/费控/EHR/企查查同步数据 |

## 依赖关系

- `aip-data-repository` 依赖：`aip-common`
- `aip-data-ingestion` 依赖：`aip-common`、`aip-data-repository`

## 包含内容

详见各子模块 README：
- [aip-data-repository/README.md](aip-data-repository/README.md)
- [aip-data-ingestion/README.md](aip-data-ingestion/README.md)
