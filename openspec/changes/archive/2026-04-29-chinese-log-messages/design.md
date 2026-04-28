## Context

当前 7 个 Java 文件中的 19 处日志消息使用英文文本。团队使用中文沟通，日志改为中文可提升生产排障效率。

## Goals / Non-Goals

**Goals:**
- 将所有 `log.info/warn/error/debug` 的英文消息改为中文
- 保持日志级别、变量占位符 `{}`、异常堆栈不变

**Non-Goals:**
- 不改变日志级别
- 不修改业务逻辑、API、测试
- 不修改变量名、类名、包名

## Decisions

仅替换日志消息字符串本身，不动调用上下文。例如：
- `log.error("State '{}' action failed", stateName, e)` → `log.error("状态 '{}' 执行失败", stateName, e)`
- 异常堆栈 `e` 保持不变，仍为英文（Java 标准异常）

## Risks / Trade-offs

- **风险**: Java 标准异常消息仍为英文，日志中会出现中英文混用 → 可接受，异常堆栈不可控也不应拦截
- **影响**: 依赖日志英文文本做正则解析的外部系统可能受影响 → 本项目无此类外部消费者
