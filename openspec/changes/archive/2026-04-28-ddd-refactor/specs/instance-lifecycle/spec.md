## ADDED Requirements

### Requirement: InstanceExecutionService（application 层）

系统 SHALL 在 `application` 层提供 `InstanceExecutionService` 应用服务，通过构造器注入 `InstanceRepository`、`SnapshotRepository` 和 `ObjectMapper`，以 `StateMachine` 实例为方法参数，编排实例的创建、恢复、重试、挂起等生命周期操作。

#### Scenario: 创建新实例
- **WHEN** 调用 `execute(stateMachine, context, businessId)`
- **THEN** 服务 SHALL 创建实例记录、调用聚合根的 `transitionTo()` 方法、循环执行直到终端状态、记录快照、返回 ExecuteResult

#### Scenario: 按业务 ID 恢复挂起实例
- **WHEN** 调用 `resumeByBusinessId(stateMachine, businessId, expectedState, contextMerger)`
- **THEN** 服务 SHALL 通过 stateMachine.getName() 查找实例、调用聚合根校验当前状态、从快照恢复上下文、应用合并、调用聚合根 `transitionTo()` 继续执行

#### Scenario: 按实例 ID 恢复挂起实例
- **WHEN** 调用 `resumeByInstanceId(stateMachine, instanceId, expectedState, contextMerger)`
- **THEN** 服务 SHALL 查找实例、校验状态、恢复上下文、继续执行

#### Scenario: 重试失败实例（自带上下文）
- **WHEN** 调用 `retryWithCustomContext(stateMachine, instanceId, context)`
- **THEN** 服务 SHALL 校验实例状态为 FAILED、调用聚合根 `recordRetry()` 重置计数、从当前状态重新执行

#### Scenario: 重试失败实例（自动恢复上下文）
- **WHEN** 调用 `retry(stateMachine, instanceId)`
- **THEN** 服务 SHALL 从最新失败快照的 inputJson 自动反序列化上下文、区分 NODE 失败和 ROUTE 失败、分别处理：NODE 失败重新执行当前状态，ROUTE 失败从匹配的目标状态继续并记录新快照

#### Scenario: 拒绝非失败实例重试
- **WHEN** 对非 FAILED 状态的实例调用 retry
- **THEN** 服务 SHALL 抛出 StateMachineException

### Requirement: 执行循环共享逻辑

系统 SHALL 在 InstanceExecutionService 中提供共享的执行循环，供 execute、resume、retry 统一调用。循环中调用 `StateMachine` 的纯领域方法获取下一状态，调用聚合根的方法更新实例状态。

#### Scenario: 执行循环终止条件
- **WHEN** 执行循环遇到终端状态（无出边）
- **THEN** 循环 SHALL 正常退出，调用聚合根 `complete()` 标记 COMPLETED

#### Scenario: 执行循环路由失败
- **WHEN** 执行循环遇到有出边但无匹配条件的状态
- **THEN** 循环 SHALL 终止，调用聚合根 `fail(errorMessage)` 标记 FAILED

#### Scenario: 执行循环状态 Action 失败
- **WHEN** 状态 Action 抛出异常
- **THEN** 服务 SHALL 记录失败快照、按 RetryPolicy 尝试重试、超过最大次数后调用聚合根 `fail()` 标记 FAILED

#### Scenario: 最大迭代保护
- **WHEN** 执行循环超过状态数 × (最大重试 + 1) + 1 次迭代
- **THEN** 服务 SHALL 终止循环并抛出 ExecutionExceededException

### Requirement: 事务管理

系统 SHALL 在 InstanceExecutionService 中管理事务边界。事务管理 SHALL 通过 Spring `@Transactional` 注解或 `UnitOfWork` 抽象实现，确保实例状态变更和快照记录在同一事务中。

#### Scenario: 事务提交
- **WHEN** 执行循环正常完成
- **THEN** 实例状态和所有快照记录在同一事务中提交

#### Scenario: 事务回滚
- **WHEN** 执行过程中抛出未预期异常
- **THEN** 当前事务 SHALL 回滚
