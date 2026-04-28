## Why

`InstanceExecutionService.executeLoop` 方法 96 行、圈复杂度 12、嵌套 5 层，承担 Action 执行、重试逻辑、挂起检查、路由查找等多项职责。`resumeByBusinessId` 与 `resumeByInstanceId` 高度重复。循环内 8 次 `requireInstance` 导致冗余 DB 查询。

## What Changes

- **P1**: 拆分 `executeLoop` 为 `executeAction()`（Action 执行 + 成功/失败快照 + 重试）、`resolveNextState()`（路由查找 + 路由快照）、`applyTransition()`（状态转换），将方法行数降至 ~40 行
- **P2**: 提取 `resume()` 私有方法，消除 `resumeByBusinessId` 和 `resumeByInstanceId` 的重复逻辑
- **P3**: 利用 `save()` 返回值传递更新后的 `InstanceData`，循环入口查一次，后续复用，消除 7 次冗余查询

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- `InstanceExecutionService.java` — 核心重构，方法拆分去重
- 不影响 API、测试或业务逻辑（行为不变）
