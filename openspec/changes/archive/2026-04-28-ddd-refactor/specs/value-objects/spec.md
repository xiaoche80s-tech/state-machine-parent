## ADDED Requirements

### Requirement: InstanceId 值对象

系统 SHALL 提供 `InstanceId` 值对象，封装实例唯一标识符，支持自动生成和校验。

#### Scenario: 生成新 ID
- **WHEN** 调用 `InstanceId.generate()`
- **THEN** 返回基于 UUID 的新 InstanceId

#### Scenario: 拒绝空值
- **WHEN** 用 null 或空白字符串构造 InstanceId
- **THEN** 系统 SHALL 抛出 IllegalArgumentException

#### Scenario: 值相等
- **WHEN** 两个 InstanceId 的 value 相同
- **THEN** 它们的 equals() 和 hashCode() SHALL 返回一致

### Requirement: DefinitionId 值对象

系统 SHALL 提供 `DefinitionId` 值对象，封装定义唯一标识符。

#### Scenario: 生成新 ID
- **WHEN** 调用 `DefinitionId.generate()`
- **THEN** 返回基于 UUID 的新 DefinitionId

#### Scenario: 拒绝空值
- **WHEN** 用 null 或空白字符串构造 DefinitionId
- **THEN** 系统 SHALL 抛出 IllegalArgumentException

### Requirement: BusinessId 值对象

系统 SHALL 提供 `BusinessId` 值对象，封装业务标识符（如订单号、运单号）。

#### Scenario: 允许空值
- **WHEN** 用 null 构造 BusinessId
- **THEN** 系统 SHALL 允许（业务 ID 为可选字段）

#### Scenario: 非空值校验
- **WHEN** 用非空字符串构造 BusinessId
- **THEN** 返回有效的 BusinessId 实例

### Requirement: MachineName 值对象

系统 SHALL 提供 `MachineName` 值对象，封装状态机名称（如 "order-process"）。

#### Scenario: 拒绝空值
- **WHEN** 用 null 或空白字符串构造 MachineName
- **THEN** 系统 SHALL 抛出 IllegalArgumentException

### Requirement: StateName 值对象

系统 SHALL 提供 `StateName` 值对象，封装状态名称（如 "check-inventory"）。

#### Scenario: 拒绝空值
- **WHEN** 用 null 或空白字符串构造 StateName
- **THEN** 系统 SHALL 抛出 IllegalArgumentException

### Requirement: SnapshotId 值对象

系统 SHALL 提供 `SnapshotId` 值对象，封装快照唯一标识符。

#### Scenario: 生成新 ID
- **WHEN** 调用 `SnapshotId.generate()`
- **THEN** 返回基于 UUID 的新 SnapshotId

#### Scenario: 拒绝空值
- **WHEN** 用 null 或空白字符串构造 SnapshotId
- **THEN** 系统 SHALL 抛出 IllegalArgumentException

### Requirement: InstanceStatus 枚举值对象

系统 SHALL 提供 `InstanceStatus` 枚举值对象，封装实例生命周期状态（RUNNING、SUSPENDED、COMPLETED、FAILED）。枚举 SHALL 为不可变类型，无 setter。

#### Scenario: 枚举值完整
- **WHEN** 访问 InstanceStatus 的全部枚举值
- **THEN** SHALL 包含 RUNNING、SUSPENDED、COMPLETED、FAILED 四个值

#### Scenario: 枚举不可变
- **WHEN** 编译 InstanceStatus 枚举
- **THEN** SHALL 无 setter 方法，为不可变类型

### Requirement: ExecutionStatus 枚举值对象

系统 SHALL 提供 `ExecutionStatus` 枚举值对象，封装快照执行状态（SUCCESS、FAILED）。枚举 SHALL 为不可变类型，无 setter。

#### Scenario: 枚举值完整
- **WHEN** 访问 ExecutionStatus 的全部枚举值
- **THEN** SHALL 包含 SUCCESS、FAILED 两个值

#### Scenario: 枚举不可变
- **WHEN** 编译 ExecutionStatus 枚举
- **THEN** SHALL 无 setter 方法，为不可变类型

### Requirement: RetryPolicy 值对象

系统 SHALL 将 `RetryPolicy` 建模为不可变值对象，包含最大重试次数、初始延迟、退避策略。`StateMachine` 的 `retryPolicy` 字段 SHALL 使用此值对象。

#### Scenario: 创建策略
- **WHEN** 通过 Builder 创建 RetryPolicy
- **THEN** 返回不可变实例，无 setter

#### Scenario: 计算延迟
- **WHEN** 调用 `getDelayForAttempt(attempt)`
- **THEN** 返回指数退避延迟值

### Requirement: 值对象与字符串的互操作

系统 SHALL 保证值对象可以与现有字符串 API 互操作，不破坏向后兼容。

#### Scenario: 值对象转字符串
- **WHEN** 调用值对象的 `value()` 方法
- **THEN** 返回原始字符串值，可用于现有 API
