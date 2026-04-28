## Context

`InstanceExecutionService.executeLoop` 是核心执行方法，96 行、圈复杂度 12、嵌套 5 层，承担 Action 执行、重试逻辑、挂起检查、路由查找等多项职责。`resumeByBusinessId` 与 `resumeByInstanceId` 重复约 30 行。循环内 8 次 `requireInstance` 导致冗余 DB 查询。

## Goals / Non-Goals

**Goals:**
- 降低 executeLoop 行数至 ~50、复杂度至 ~5
- 消除 resume 方法间重复
- 消除 executeLoop 循环内的冗余 DB 查询

**Non-Goals:**
- 不改变任何业务行为
- 不修改 Repository 接口签名（save 已返回 InstanceData）
- 不引入新的并发控制

## Decisions

### P1: 拆分 executeLoop

将 Action 执行+重试提取为 `executeAction()`，使用内部 `while(true)` 循环处理重试（非递归，避免栈溢出）。`executeLoop` 仅保留循环守卫、状态查找、挂起检查、路由逻辑。

### P2: 提取 resume 公共逻辑

`resumeByBusinessId` 和 `resumeByInstanceId` 仅在实例查找方式上不同，后续逻辑完全一致。提取 `resume(InstanceData, StateMachine, StateName, Consumer)` 私有方法。

### P3: 利用 save() 返回值

`InstanceRepository.save()` 已返回 `InstanceData`。`executeLoop` 入口处查一次 `current = requireInstance(instanceId)`，后续循环内用 `current = instanceRepo.save(current.withUpdatedState(...))` 传递更新后的数据，不再重复查询。

## Risks / Trade-offs

- **并发写入覆盖**: `current` 在循环内持有旧数据，`save` 是全字段 UPDATE。如果有外部进程同时修改同一实例，循环内的 save 会覆盖外部写入。但 executeLoop 是同步执行，正常不会有并发。外部管理接口的干预是有意为之，循环覆盖回写是合理行为。
- **lambda final 限制**: `current` 在循环中被重新赋值，lambda 需要 final 引用。使用 `final InstanceData currentRef = current` 捕获当前值。
