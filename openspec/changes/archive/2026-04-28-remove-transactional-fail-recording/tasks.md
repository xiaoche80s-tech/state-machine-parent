## 1. Remove @Transactional from InstanceExecutionService

- [ ] 1.1 移除 5 个公共方法上的 `@Transactional` 注解
- [ ] 1.2 移除 `import org.springframework.transaction.annotation.Transactional`

## 2. Verify failure data persistence

- [ ] 2.1 确认 `executeLoop` 内 action 异常时已写入 FAILED 快照和 FAILED 实例状态
- [ ] 2.2 确认 `retry()` 和 `retryWithCustomContext()` 的异常路径同样保留失败数据
- [ ] 2.3 确认 `resumeByBusinessId()` 和 `resumeByInstanceId()` 的异常路径同样保留失败数据

## 3. Run tests

- [ ] 3.1 `mvn test -pl state-machine-boot-starter` 全量通过
- [ ] 3.2 `mvn test -Dtest=StateMachineIntegrationTest` 验证失败场景数据落盘
