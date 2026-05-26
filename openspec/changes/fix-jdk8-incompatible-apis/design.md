## Context

`jdk8` 分支有 3 类 JDK 9+ API 调用导致编译失败。这些是在之前开发中引入的，JDK 8 和 JDK 9+ 的 String/Optional/InputStream API 存在差异。

## Goals / Non-Goals

**Goals:**
- 所有代码在 JDK 8 下编译通过
- 行为等价替换，不改变运行时语义

**Non-Goals:**
- 不修复其他 JDK 兼容性问题（如 Lombok 注解等）
- 不引入第三方工具类库（如 Apache Commons Lang）

## Decisions

### 决策 1：`String.isBlank()` → `value != null && value.trim().isEmpty()`

`isBlank()` 判断 null-safe 且忽略空白字符。JDK 8 中等价写法：
- `value == null || value.trim().isEmpty()` → 判断为空白
- `value != null && !value.trim().isEmpty()` → 判断不为空白

### 决策 2：`Optional.isEmpty()` → `!Optional.isPresent()`

直接取反，行为完全等价。

### 决策 3：`InputStream.readAllBytes()` → `ByteArrayOutputStream` 手动读取

JDK 8 中 `InputStream` 没有 `readAllBytes()` 方法。使用 `ByteArrayOutputStream` 循环读取字节数组。
