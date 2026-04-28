## ADDED Requirements

### Requirement: StateMachine 不可变领域对象

系统 SHALL 将 `StateMachine` 作为不可变的领域对象，同时承载转换规则引擎职责。`StateMachine` 封装状态列表、转换列表、重试策略的定义和校验，不变式：至少有一个状态、至少有一个初始转换。StateMachine 不继承 AggregateRoot（非运行时业务实体，不产生领域事件），提供 `hasState(StateName)` 方法供聚合根校验状态合法性。

#### Scenario: 创建有效的状态机
- **WHEN** 使用至少一个状态和一个转换通过 Builder 创建状态机
- **THEN** 状态机创建成功并分配唯一版本标识

#### Scenario: 拒绝无状态的状态机
- **WHEN** 尝试构建不含任何状态的状态机
- **THEN** Builder SHALL 抛出 IllegalArgumentException

#### Scenario: 状态列表不可变
- **WHEN** 状态机创建后尝试修改其状态列表
- **THEN** 系统 SHALL 返回不可变集合，拒绝修改

#### Scenario: hasState 校验
- **WHEN** 调用 `hasState(stateName)` 且状态名存在于 states 列表中
- **THEN** 返回 true

#### Scenario: hasState 拒绝
- **WHEN** 调用 `hasState(stateName)` 且状态名不存在于 states 列表中
- **THEN** 返回 false

### Requirement: StateMachineInstance 聚合根（富行为）

系统 SHALL 提供 `StateMachineInstance` 作为实例的独立聚合根，内置状态流转的不变式校验和富行为方法。聚合根 SHALL 提供 `transitionTo(StateName, StateMachine<?>)`、`markSuspended()`、`recordRetry()`、`complete()`、`fail(String)` 等方法，在方法内部校验状态合法性。`transitionTo()` 方法 SHALL 接受 `StateMachine<?>` 参数（通配符消除泛型泄漏）以校验目标状态是否存在于定义中。聚合根 SHALL 持有 `DefinitionId` 字段作为 StateMachine 的持久化标识关联。

#### Scenario: 创建新实例
- **WHEN** 基于 StateMachine 和业务 ID 创建实例
- **THEN** 实例 SHALL 初始状态为 InstanceStatus.RUNNING，当前状态为 StateName（StateMachine 的初始状态），记录 DefinitionId，并收集 InstanceStarted 事件

#### Scenario: 状态转换合法性校验（借助 StateMachine.hasState）
- **WHEN** 调用 `transitionTo(targetState, machine)` 且 `machine.hasState(targetState)` 返回 false
- **THEN** 系统 SHALL 拒绝并抛出 StateMachineException，不改变当前状态

#### Scenario: 挂起状态标记
- **WHEN** 调用 `markSuspended()`
- **THEN** 实例状态 SHALL 变为 InstanceStatus.SUSPENDED，当前状态保持不变，并收集 InstanceSuspended 事件

#### Scenario: 终端状态不可变更
- **WHEN** 对 InstanceStatus.COMPLETED 或 InstanceStatus.FAILED 状态的实例调用 `transitionTo()` 或 `markSuspended()`
- **THEN** 系统 SHALL 拒绝变更并抛出 StateMachineException

#### Scenario: 完成状态
- **WHEN** 调用 `complete()`
- **THEN** 状态 SHALL 变为 InstanceStatus.COMPLETED，收集 InstanceCompleted 事件

#### Scenario: 失败状态
- **WHEN** 调用 `fail(errorMessage)`
- **THEN** 状态 SHALL 变为 InstanceStatus.FAILED，记录错误信息，收集 InstanceFailed 事件

#### Scenario: 重试计数
- **WHEN** 调用 `recordRetry()` 且当前重试次数已达上限
- **THEN** 系统 SHALL 拒绝并抛出 StateMachineException

### Requirement: ExecutionSnapshot 聚合根（独立聚合，不可变）

系统 SHALL 提供 `ExecutionSnapshot` 作为独立的聚合根，通过 `InstanceId` 与实例关联，拥有自己的 `SnapshotRepository`。快照聚合根创建后不可变，不提供任何修改方法。路由评估失败 SHALL 记录新的快照而非修改旧快照。

#### Scenario: 创建成功执行快照（不可变）
- **WHEN** 状态 Action 执行成功，调用 `ExecutionSnapshot.createSuccess(...)`
- **THEN** 创建 ExecutionStatus.SUCCESS 状态的快照，包含 inputJson 和 outputJson，创建后不可修改

#### Scenario: 创建失败执行快照（不可变）
- **WHEN** 状态 Action 抛出异常，调用 `ExecutionSnapshot.createFailed(...)`
- **THEN** 创建 ExecutionStatus.FAILED 状态的快照，包含 inputJson 和 errorMessage，创建后不可修改

#### Scenario: 创建路由快照（不可变）
- **WHEN** 状态转换路由评估成功，调用 `ExecutionSnapshot.createRoute(...)`
- **THEN** 创建 ExecutionStatus.SUCCESS 的 ROUTE 类型快照，记录目标状态，创建后不可修改

#### Scenario: 快照通过 InstanceId 关联
- **WHEN** 查询某实例的所有快照
- **THEN** 系统 SHALL 通过 `InstanceId` 返回该实例的快照列表，按执行时间排序

#### Scenario: 路由评估失败记录新快照
- **WHEN** 路由评估失败（有出边但无匹配条件）
- **THEN** 系统 SHALL 创建 ExecutionStatus.FAILED 的新快照，而非修改已有的路由快照
