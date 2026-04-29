## Why

当前项目基于 JDK 17 + Spring Boot 3.2.5 构建，但老项目使用 JDK 8 + Spring Boot 2.x 环境，无法直接引入此状态机库。需要提供一个 JDK 8 兼容版本，使其能在老项目中直接使用。

## What Changes

- 将 `java.version` 从 17 降为 8
- 将 Spring Boot 依赖从 3.2.5 降级到 2.7.x（最后一个支持 JDK 8 的 2.x 版本）
- 替换所有 JDK 9+ API 用法（`List.of()`、`var` 等）为 JDK 8 兼容写法
- 将 `javax.annotation` 相关包从 `jakarta.*` 改回 `javax.*`（Spring Boot 3 使用 jakarta 命名空间）
- 保持 API 接口和功能不变，仅做兼容性适配

## Capabilities

### New Capabilities
- `jdk8-compatibility`: 整个库在 JDK 8 + Spring Boot 2.7.x 环境下的编译、运行和测试兼容性

### Modified Capabilities
无

## Impact

- 全部 3 个 pom.xml（根、starter、demo）的 Java 版本和 Spring Boot 版本
- 所有使用 `var` 局部变量类型的 Java 源文件
- 所有使用 `List.of()`、`Set.of()`、`Map.of()` 的代码改为 `Arrays.asList()` 等
- 所有 `jakarta.*` 导入改为 `javax.*`
- Spring Boot 3 特有的 API 需要替换为 2.x 等价物
- 测试框架版本可能需调整
