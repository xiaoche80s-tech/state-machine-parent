## Why

`jdk8` 分支编译失败，因为代码中使用了 JDK 9+ 引入的 API：`String.isBlank()`、`Optional.isEmpty()`、`InputStream.readAllBytes()`。这些方法在 JDK 8 中不存在。

## What Changes

- `String.isBlank()` → `String.trim().isEmpty()`（12 处）
- `Optional.isEmpty()` → `!Optional.isPresent()`（2 处）
- `InputStream.readAllBytes()` → 手动读取字节数组（1 处）

## Capabilities

### New Capabilities

- `jdk8-api-compat`: JDK 8 兼容 API 替换规范，确保所有代码可在 JDK 8 下编译

### Modified Capabilities

<!-- 无现有规格变更 -->

## Impact

- 7 个 Java 文件的 API 调用替换
- 行为不变，仅编译兼容性修复
