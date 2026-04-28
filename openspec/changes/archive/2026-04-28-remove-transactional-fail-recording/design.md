## Context

当前 `InstanceExecutionService` 的 5 个公共方法（execute/resumeByBusinessId/resumeByInstanceId/retryWithCustomContext/retry）都加了 `@Transactional`。

`executeLoop` 内部已经有 try-catch：action 失败时先写入 FAILED 快照、更新实例状态为 FAILED，然后抛出 `StateMachineException`。但由于外层有事务，这个异常会导致整个事务回滚——所有写入（包括成功快照、失败快照、实例状态）全部丢失。

**用户症状**：在控制台调用 execute 失败后，查不到任何实例和快照记录，无法排查问题。

## Goals / Non-Goals

**Goals:**
- 异常发生时，已写入的快照（含失败快照）和实例的 FAILED 状态必须保留在数据库中
- 保持现有方法签名和异常行为不变（调用方仍收到 StateMachineException）
- 移除 `@Transactional`，用手动 try-catch-finally 保证失败数据落盘

**Non-Goals:**
- 不引入新的补偿机制或 SAGA
- 不改变重试策略和指数退避逻辑
- 不修改 snapshot/instance 的数据模型

## Decisions

### 决策：移除 @Transactional，在公共方法层 catch 异常后先保存失败状态再 re-throw

**方案对比：**

| 方案 | 优点 | 缺点 |
|------|------|------|
| A. 移除 @Transactional，executeLoop 内异常时保存失败快照+状态 | 简单直接，数据一定落盘 | executeLoop 内部已经做了这件事，但抛异常后外层事务回滚抵消了 |
| B. @Transactional(noRollbackFor = StateMachineException.class) | 最小改动 | Spring 的 noRollbackFor 只控制不回滚，但 executeLoop 内抛异常后状态已经是 FAILED，不会被重复写入 |

选择 **方案 A**：直接移除 `@Transactional`。原因：
1. `executeLoop` 内部已经正确处理了失败场景的快照和状态写入
2. 各方法的公共逻辑（创建实例、查找定义等）不需要原子性保证——即使中途失败，部分写入的数据（如已创建的实例记录）也比完全回滚更有排查价值
3. 各方法本身已经是幂等的，不需要事务保护

### 具体改动

每个公共方法的 executeLoop 调用外包裹 try-catch：
- 正常执行：直接返回
- 异常：executeLoop 内部已经写入了失败快照和 FAILED 状态，外层只需记录日志后 re-throw

## Risks / Trade-offs

**[Risk] 多个 DB 写入不原子** → 如果 executeLoop 中间进程被 kill，可能出现实例状态不一致。Mitigation：这是极低概率事件，且当前的事务方案在异常时同样无法保证一致性（回滚后数据全丢）。失败可见性比原子性更重要。

**[Risk] 并发调用同一实例** → 无事务保护后，两个线程可能同时更新同一实例。Mitigation：现有 `tryMarkRunningFromSuspended` 使用 SQL 行级条件更新，仍有效。其他路径依赖业务层幂等。
