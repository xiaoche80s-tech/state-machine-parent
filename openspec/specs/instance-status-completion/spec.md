## ADDED Requirements

### Requirement: 路由失败时标记为 FAILED

当状态机在当前状态定义了出边过渡，但所有过渡的条件都不匹配时，实例 MUST 被标记为 `FAILED` 状态，而非 `COMPLETED`。错误消息 MUST 包含当前状态名称和所有已定义的过渡目标列表，以便排查。

#### Scenario: 所有过渡条件都不匹配
- **WHEN** 状态机执行到一个状态，该状态定义了至少一个出边过渡，但所有过渡的条件对当前上下文都返回 false
- **THEN** 实例状态被更新为 `FAILED`，错误消息格式为 "No matching transition from state '{stateName}'. Available transitions: {to1}, {to2}, ..."

#### Scenario: 仅有一个过渡且条件不匹配
- **WHEN** 状态 `process-payment` 定义了两个过渡：到 `await-ship-confirm`（条件 `paymentSuccess && !routeFailed && random >= 0.5`）和到 `order-failed`（条件 `!paymentSuccess || routeFailed`），当前上下文 `paymentSuccess=true`、`routeFailed=false`、`random=0.3`
- **THEN** 两个条件均不匹配，实例标记为 `FAILED`，错误消息包含 "process-payment" 和可用过渡 "await-ship-confirm, order-failed"

### Requirement: 正常完成时标记为 COMPLETED

当状态机执行到一个状态，该状态没有任何出边过渡定义（真正的终端状态）时，实例 MUST 被标记为 `COMPLETED`。此行为与现有逻辑一致，不做变更。

#### Scenario: 终端状态无出边
- **WHEN** 状态机执行到一个状态，该状态在 transitions 列表中没有任何以它为 `from` 的过渡
- **THEN** 实例状态被更新为 `COMPLETED`，流程正常结束
