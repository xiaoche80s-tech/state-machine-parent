## Why

当状态机在某个状态下没有任何过渡条件匹配时，`StateMachine.executeLoop()` 将实例标记为 `COMPLETED`（已完成），这是一种语义错误。实际行为是"路由失败"——状态机定义了过渡规则但无一命中，流程被意外截断。这导致实例在中间状态（如 `process-payment`）显示为"已完成"，与实际的执行快照（只有 2 个节点执行）严重不一致，误导运维人员。

## What Changes

- `StateMachine.executeLoop()` 中，区分"无出边（终端状态）"和"有出边但无匹配条件"两种情况
- 当存在从当前状态定义的过渡但无一条条件匹配时，标记为 `FAILED` 而非 `COMPLETED`
- 错误消息需包含当前状态名和已定义的过渡目标，便于排查
- 前端无需修改——`FAILED` 状态已有正确展示

## Capabilities

### New Capabilities
- `instance-status-completion`: 区分正常完成与路由失败两种结束条件，确保状态语义正确

### Modified Capabilities
<!-- 无现有 spec 文件 -->

## Impact

- **后端**: `StateMachine.java` — `executeLoop()` 方法第 194-198 行的状态判定逻辑
- **API**: `COMPLETED` 的触发条件收紧，原被误标为 `COMPLETED` 的实例将变为 `FAILED`
- **前端**: 无需修改，`FAILED` 状态已有完整的 UI 展示
- **测试**: `StateMachineCoreTest.java` 需要补充路由失败的测试用例
