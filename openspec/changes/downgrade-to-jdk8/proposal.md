## Why

老项目依赖 JDK 8 环境，当前 state-machine-boot-starter 使用 Java 17 + Spring Boot 3.2.5，无法被老项目引入。需要降级到 JDK 8 + Spring Boot 2.7.x 以保持兼容性。

## What Changes

- Java 版本从 17 降至 8
- Spring Boot 从 3.2.5 降至 2.7.18（最后一个支持 JDK 8 的 2.x 版本）
- `record` 类型改为传统 Java 类（JDK 14+ 特性）
- `var` 局部变量类型推断改为显式类型声明（JDK 10+ 特性）
- Maven 编译插件配置调整为 source/target 8
- Maven Surefire 插件版本调整为兼容 JDK 8 的版本

## Capabilities

### New Capabilities
<!-- 无新能力引入，仅为运行时环境降级 -->

### Modified Capabilities
<!-- 无现有能力的需求变更，功能行为保持不变 -->

## Impact

- `pom.xml`（三个模块）：Java 版本、Spring Boot 版本、插件版本
- `src/main/java` 中所有 `record` 类：改为 class + 构造函数 + getter + equals/hashCode
- `src/main/java` 和 `src/test/java` 中所有 `var`：改为显式类型
- 已有的 `javax.*` 导入无需变更（Spring Boot 2.x 使用 javax，代码中已正确使用）
- 功能行为保持不变，API 契约不变
