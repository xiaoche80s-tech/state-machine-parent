## Why

当前代码中的日志消息全部使用英文，与团队中文沟通习惯不一致，生产排查时需要额外翻译才能理解日志含义。将日志改为中文可以提升可读性和排障效率。

## What Changes

- 将 `InstanceExecutionService` 中 4 处英文日志改为中文
- 将 `StateMachineRegistry` 中 2 处英文日志改为中文
- 将 `ConsoleController` 中 3 处英文日志改为中文
- 将 `StateMachineEndpoint` 中 1 处英文日志改为中文
- 将 `DdlInitializer` 中 4 处英文日志改为中文
- 将 `StateMachineAutoConfiguration` 中 3 处英文日志改为中文
- 将 `JdbcInstanceRepository` 中 2 处英文日志改为中文

仅修改日志消息文本，不改变日志级别、参数占位符、异常堆栈等结构。

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- 7 个 Java 文件中的 19 处 `log.info/warn/error` 消息文本
- 不影响 API、接口、测试或业务逻辑
- 日志中变量占位符（`{}`）和异常堆栈保持不变
