# aip-common

> 公共基础模块：定义跨模块共享的模型、枚举、异常和统一响应结构。

## 模块职责

- 统一 `ResponseResult` / `PageResult` 返回格式。
- 提供全局枚举（任务状态、Agent 角色、报告风格等）。
- 提供通用异常与全局异常处理。
- 提供 LLM 抽象接口与请求/响应模型。

## 典型内容

- `com.sinosig.aip.common.core.enums`
- `com.sinosig.aip.common.core.response`
- `com.sinosig.aip.common.core.exception`
- `com.sinosig.aip.common.ai.service`
- `com.sinosig.aip.common.ai.model`

## 依赖关系

- 被所有模块依赖，不依赖业务模块。

## 使用示例

```java
return ResponseResult.success(data);
throw new AgentException("任务不存在");
```
