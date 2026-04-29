## Context

当前项目基于 JDK 17 + Spring Boot 3.2.5，使用了多项 JDK 9+ 语法特性（`var`、`List.of()`、`record`）和 Spring Boot 3 的依赖体系。目标是在 JDK 8 + Spring Boot 2.7.x 环境中可用。

关键数据：
- `var` 局部变量：69 处（starter 20 + test 43 + demo 6）
- `List.of()/Set.of()/Map.of()`：37 处
- `record` 类型：18 个（主要在 domain 层）
- `jakarta.*` 包引用：0 处（本项目不直接使用 jakarta 包）

## Goals / Non-Goals

**Goals:**
- 项目可在 JDK 8 下编译和运行
- Spring Boot 版本降级至 2.7.18（最后一个 2.x LTS 版本）
- 所有现有功能（状态机执行、快照、重试、Web 控制台、Actuator 端点）保持不变
- 全部测试通过

**Non-Goals:**
- 不引入第三方兼容库（如 lombok 等）
- 不改变现有 API 接口
- 不引入新的模块或功能

## Decisions

1. **Spring Boot 2.7.18**：选择 2.7.18 而非 2.6.x 或更早版本，因为它是 2.x 系列的最终维护版本，安全补丁最新，且与 JDK 8 完全兼容。

2. **record → 传统 class**：`record` 是 JDK 14+ 特性。将所有 `record` 改为传统不可变 class（private final 字段 + 构造函数 + getter）。不使用 lombok，保持零额外依赖。

3. **var → 显式类型**：所有 `var` 替换为显式类型声明。这是纯机械替换，不影响逻辑。

4. **List.of()/Set.of()/Map.of() → Arrays.asList()/Collections.unmodifiableList()**：JDK 8 中用 `Arrays.asList()` 或 `Collections` 工具类替代。注意 `List.of()` 不允许 null 元素，`Arrays.asList()` 允许 null，需要在替换时保持一致性（使用 `Collections.unmodifiableList(Arrays.asList(...))`）。

5. **保持测试框架不变**：Spring Boot 2.7.x 自带 JUnit 5.8+，与现有测试兼容。

## Risks / Trade-offs

- **[Risk]** `record` 改为传统 class 会丢失自动生成的 `equals()`/`hashCode()`/`toString()`，需要手动实现 → **Mitigation**: 对 domain 层的 record 逐一补充这三个方法
- **[Risk]** Spring Boot 3 → 2.7 降级后，部分 API 可能有细微差异（如自动装配机制） → **Mitigation**: 以全部测试通过为验收标准
- **[Risk]** `List.of()` 返回的不可变列表与 `Arrays.asList()` 行为不完全一致（如 `of()` 拒绝 null） → **Mitigation**: 替换时审查每个调用点的 null 语义
- **[Trade-off]** 手动实现不可变 class 增加了代码量（每个 record 约 30 行 → 约 80 行），但保持了零依赖

## Migration Plan

1. 在 `feature/jdk8-compatibility` 分支上实施
2. 先改 pom.xml 版本，确保依赖可解析
3. 逐个替换 `record` → class
4. 逐个替换 `var` → 显式类型
5. 逐个替换 `List.of()` 等 → JDK 8 等价物
6. 编译 + 全量测试
7. 合并到主分支

## Open Questions

无
