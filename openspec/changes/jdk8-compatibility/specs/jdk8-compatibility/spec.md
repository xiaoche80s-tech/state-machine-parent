## ADDED Requirements

### Requirement: JDK 8 编译兼容
项目所有源代码（state-machine-boot-starter、state-machine-demo 及测试）必须能够在 JDK 8 编译器下通过编译。

#### Scenario: JDK 8 编译成功
- **WHEN** 使用 JDK 8 执行 `mvn clean compile`
- **THEN** 所有模块编译成功，无语法错误

### Requirement: Spring Boot 2.7.x 兼容
项目依赖的 Spring Boot 版本从 3.2.5 降至 2.7.18，所有自动装配、条件注解、Actuator 端点功能正常工作。

#### Scenario: Spring Boot 2.7 启动成功
- **WHEN** 在 Spring Boot 2.7.18 环境下启动应用
- **THEN** 状态机 Bean 正常注册，Web 控制台和 Actuator 端点可访问

### Requirement: 移除 JDK 9+ 语法特性
所有 Java 源代码不得使用 `var` 局部变量类型、`record` 类型、`List.of()`/`Set.of()`/`Map.of()` 等 JDK 9+ 语法。

#### Scenario: var 替换为显式类型
- **WHEN** 扫描所有 .java 文件
- **THEN** 不存在 `var` 关键字用法

#### Scenario: record 替换为传统 class
- **WHEN** 扫描所有 .java 文件
- **THEN** 不存在 `record` 关键字用法

#### Scenario: 集合工厂方法替换
- **WHEN** 扫描所有 .java 文件
- **THEN** 不存在 `List.of()`、`Set.of()`、`Map.of()` 调用

### Requirement: 功能回归测试通过
降级后所有现有测试用例必须通过，保证状态机执行、快照记录、失败重试、Web 控制台等功能与降级前一致。

#### Scenario: 全量测试通过
- **WHEN** 执行 `mvn test`
- **THEN** 所有测试通过，无失败、无错误
