## Context

当前 state-machine-parent 项目使用 Java 17 + Spring Boot 3.2.5，使用了 `record`（JDK 14+）、`var`（JDK 10+）等语言特性。老项目运行在 JDK 8 环境，无法引入此依赖。

代码中 `javax.*` 导入已正确使用（非 `jakarta.*`），无需额外调整包名。

## Goals / Non-Goals

**Goals:**
- 使 state-machine-boot-starter 可在 JDK 8 + Spring Boot 2.7.x 环境中编译和运行
- 保持所有现有功能行为不变
- 保持 API 契约不变

**Non-Goals:**
- 不引入新功能或 API 变更
- 不修改业务逻辑或状态机核心行为
- 不做代码重构（仅做语言特性兼容转换）

## Decisions

### Spring Boot 版本选择：2.7.18

Spring Boot 2.7.18 是 2.7.x 最后一个版本，也是最后一个支持 JDK 8 的分支。2.7.x 仍使用 `javax.*` 命名空间，与当前代码中已有的 `javax.sql.DataSource` 等导入兼容。

### record → class 转换策略

所有 `record` 改为 `final class`，手动编写：
- 私有 final 字段
- 全参构造函数
- getter 方法（无 `is` 前缀，与 record 行为一致）
- `equals()` 和 `hashCode()`（基于所有字段）
- `toString()`（可选，便于调试）

不引入 Lombok，避免增加依赖复杂度。

### var → 显式类型

测试代码中 `var` 全部替换为显式类型声明，与 JDK 8 兼容。

### Maven 插件版本

- `maven-compiler-plugin`: source/target 改为 8，保留 `parameters` 配置（JDK 8 支持）
- `maven-surefire-plugin`: 3.2.5 兼容 JDK 8，保持不变

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| `record` 转 class 后 equals/hashCode 手写可能出错 | 仅基于字段比较，保持简单；集成测试覆盖 |
| Spring Boot 2.7.x 与 3.2.5 的自动配置差异 | 当前仅依赖 jdbc/web/actuator starter，差异极小 |
| PostgreSQL/H2 驱动版本随 Spring Boot BOM 变化 | 由 spring-boot-dependencies BOM 管理，测试验证 |
