## ADDED Requirements

### Requirement: 纯领域状态转换引擎

系统 SHALL 提供 `StateMachine` 作为纯领域对象，仅持有状态定义、转换规则和重试策略，不持有任何持久化引用、应用层服务引用或 ObjectMapper。

#### Scenario: 状态机不包含持久化逻辑
- **WHEN** 检查 StateMachine 类的字段
- **THEN** 不包含 JdbcTemplate、InstanceRepository、SnapshotRepository、ObjectMapper 等字段

#### Scenario: StateMachine 不继承 AggregateRoot
- **WHEN** 检查 StateMachine 类的继承关系
- **THEN** StateMachine SHALL 不继承 AggregateRoot，不产生领域事件

#### Scenario: 查找下一状态
- **WHEN** 给定当前 StateName 和上下文，调用状态机的转换方法
- **THEN** 返回第一个条件匹配的下一 StateName，无条件匹配返回 empty

#### Scenario: 终端状态检测
- **WHEN** StateName 没有出边转换
- **THEN** 系统 SHALL 判定为终端状态

#### Scenario: 路由失败检测
- **WHEN** StateName 有出边但所有条件均不匹配
- **THEN** 系统 SHALL 判定为路由失败，而非终端状态

### Requirement: 状态转换决策与持久化解耦

系统 SHALL 通过 application 层的 `InstanceExecutionService` 协调 `StateMachine` 的纯领域转换决策和持久化操作。StateMachine 作为参数传入 service 方法，不被 service 内部持有。

#### Scenario: 执行流程协调
- **WHEN** 调用 `execute()` 方法
- **THEN** InstanceExecutionService SHALL 创建实例记录、循环调用 StateMachine 转换、每次转换后记录快照

#### Scenario: StateMachine 作为参数传递
- **WHEN** InstanceExecutionService 需要执行状态转换
- **THEN** SHALL 将 StateMachine 实例作为方法参数传入，而非构造函数注入

### Requirement: 序列化职责归属 application 层

系统 SHALL 将上下文 JSON 序列化/反序列化逻辑放在 application 层的 `InstanceExecutionService` 中，domain 层的 StateMachine 不依赖 jackson-databind。

#### Scenario: 序列化不在 domain 层
- **WHEN** 编译 domain 模块
- **THEN** domain 模块 SHALL 不依赖 jackson-databind

#### Scenario: StateName 值对象贯穿
- **WHEN** 检查 StateMachine 的 `findNextState()` 方法签名
- **THEN** SHALL 使用 `StateName` 类型参数，而非裸 String
