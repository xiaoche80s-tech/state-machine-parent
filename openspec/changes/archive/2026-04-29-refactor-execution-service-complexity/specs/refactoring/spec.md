## ADDED Requirements

### Requirement: executeLoop 复杂度控制

executeLoop 方法行数 SHALL 不超过 60 行，圈复杂度 SHALL 不超过 6。

#### Scenario: 方法行数验证
- **WHEN** 审查 executeLoop 方法
- **THEN** 方法体不超过 60 行（不含空行和注释）

#### Scenario: 职责分离
- **WHEN** Action 执行发生异常
- **THEN** 重试逻辑在 executeAction 内部处理，不影响 executeLoop 的复杂度

### Requirement: resume 公共逻辑提取

resumeByBusinessId 和 resumeByInstanceId SHALL 共享相同的恢复逻辑实现。

#### Scenario: 按 businessId 恢复
- **WHEN** 调用 resumeByBusinessId
- **THEN** 通过公共 resume() 方法执行恢复逻辑

#### Scenario: 按 instanceId 恢复
- **WHEN** 调用 resumeByInstanceId
- **THEN** 通过公共 resume() 方法执行恢复逻辑，与 resumeByBusinessId 行为一致
