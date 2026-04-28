## Why

当前 demo 项目的状态机流程是确定性的，无法演示状态机的**失败重试**和**路由条件分支**能力。用户无法通过 demo 观察到：
- Action 执行失败后的指数退避重试行为
- 路由 condition 返回 false 时的分支切换

需要在 demo 中引入随机失败，使其成为更完整的教学/演示工具。

## What Changes

- `process-payment` 节点增加 30% 概率的 action 执行失败（抛异常，触发重试）
- `process-payment` → `await-ship-confirm` 路由增加 50% 概率的 condition 返回 false（路由到 `order-failed` 分支）

## Capabilities

### New Capabilities
- `demo-random-failures`: 为 demo 项目的订单状态机注入随机失败，覆盖 action 执行失败和路由 condition 失败两种场景

### Modified Capabilities

## Impact

- 修改 `state-machine-demo` 模块：`OrderConfig.java`、`OrderContext.java`、`ConditionFailureDemoTest.java`
- 不影响 `state-machine-boot-starter` 核心库
