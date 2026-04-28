## Why

当前 `InstanceExecutionService` 的所有方法（execute/resume/retry）都加了 `@Transactional`，导致 action 抛异常时整个事务回滚——快照和实例状态的写入全部丢失。用户无法在控制台上看到失败记录、错误信息和重试历史，排查问题缺乏数据。

## What Changes

- 移除 `InstanceExecutionService` 中所有 5 个方法的 `@Transactional` 注解
- 异常发生时，失败的快照（FAILED status）和实例的 FAILED 状态仍需要正常写入数据库
- 每个方法内部对异常进行 try-catch，先记录失败快照和更新实例状态，再抛出异常给调用方

## Capabilities

### New Capabilities
<!-- None - this is a behavior fix, not a new capability -->

### Modified Capabilities
<!-- None - existing spec requirements don't change, just the implementation -->

## Impact

- `application/InstanceExecutionService.java`：移除 `@Transactional`，调整异常处理逻辑
- `InstanceExecutionServiceTest.java`：测试需适配（不再依赖事务回滚行为）
- `StateMachineIntegrationTest.java`：集成测试验证失败场景的快照/实例持久化
