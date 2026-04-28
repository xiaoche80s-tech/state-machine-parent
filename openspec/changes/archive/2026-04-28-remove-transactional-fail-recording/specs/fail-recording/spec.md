## ADDED Requirements

### Requirement: Failed state and snapshots MUST persist after exception

当 StateMachine action 抛出异常时，已写入的失败快照（ExecutionStatus=FAILED）和实例的 FAILED 状态 MUST 保留在数据库中，不得被事务回滚清除。

#### Scenario: Action throws exception - failure data is visible
- **WHEN** `execute()` 调用且某个 state action 抛异常且重试次数耗尽
- **THEN** 数据库中 MUST 存在 FAILED 状态的 instance 记录
- **AND** 数据库中 MUST 存在 status=FAILED 的快照记录
- **AND** 调用方收到的异常中 MUST 包含失败原因

#### Scenario: Retry on failed instance - failure history preserved
- **WHEN** `retry()` 被调用且 action 再次抛异常
- **THEN** 历史失败快照 MUST 保留
- **AND** 新的失败快照 MUST 写入
- **AND** instance 状态 MUST 保持 FAILED

#### Scenario: Resume suspended instance - failure on resume is recorded
- **WHEN** `resumeByInstanceId()` 调用后 action 抛异常
- **THEN** instance 状态 MUST 更新为 FAILED
- **AND** 失败快照 MUST 写入
- **AND** 调用方收到 StateMachineException
