## 1. 值对象（基础层，所有其他任务依赖）

- [ ] 1.1 创建 `InstanceId` 值对象（record，UUID 生成，空值校验）
- [ ] 1.2 创建 `DefinitionId` 值对象（record，UUID 生成，空值校验）
- [ ] 1.3 创建 `SnapshotId` 值对象（record，UUID 生成，空值校验）
- [ ] 1.4 创建 `BusinessId` 值对象（record，允许 null，非空校验）
- [ ] 1.5 创建 `MachineName` 值对象（record，空值校验）
- [ ] 1.6 创建 `StateName` 值对象（record，空值校验）
- [ ] 1.7 创建 `InstanceStatus` 枚举值对象（RUNNING、SUSPENDED、COMPLETED、FAILED）
- [ ] 1.8 创建 `ExecutionStatus` 枚举值对象（SUCCESS、FAILED）
- [ ] 1.9 确认 `RetryPolicy` 已为不可变值对象，补充值对象规范
- [ ] 1.10 编写值对象单元测试（生成、校验、相等性）

## 2. 领域事件与聚合根基类

- [ ] 2.1 创建 `DomainEvent` 标记接口
- [ ] 2.2 创建 `AggregateRoot<ID>` 泛型基类（事件收集 / 清空方法）
- [ ] 2.3 创建领域事件类：`InstanceStarted`、`InstanceCompleted`、`InstanceFailed`、`InstanceSuspended`
- [ ] 2.4 编写聚合根基类单元测试（事件收集、清空）

## 3. 领域聚合根（富行为）

- [ ] 3.1 将 `StateMachine` 改造为不可变领域对象：移除所有持久化字段和方法，states/transitions/retryPolicy 返回不可变集合，不变式校验（至少一个状态/一个转换）。StateMachine 不继承 AggregateRoot（非运行时业务实体，不产生领域事件）
- [ ] 3.2 创建 `StateMachineInstance` 聚合根（实例 ID、定义 ID、状态流转、重试计数、业务 ID、领域事件触发）
- [ ] 3.3 为 StateMachineInstance 添加富行为方法：`transitionTo(StateName, StateMachine<?>)`、`markSuspended()`、`recordRetry()`、`complete()`、`fail(String)`，内置状态校验，字段使用 `StateName` 和 `InstanceStatus` 值对象
- [ ] 3.4 创建 `ExecutionSnapshot` 独立聚合根（instanceId 关联、快照类型、输入/输出 JSON、执行状态、尝试次数），提供不可变工厂方法 `createSuccess()`、`createFailed()`、`createRoute()`
- [ ] 3.5 编写聚合根单元测试（不变式、富行为方法、领域事件、状态转换合法性）

## 4. 仓储接口（domain 层）

- [ ] 4.1 创建 `InstanceRepository` 接口（findById、save、findByBusinessId、tryMarkRunningFromSuspended、过滤查询，使用值对象参数）
- [ ] 4.2 创建 `SnapshotRepository` 接口（findByInstanceId、save，无 update 方法以保持不可变语义）
- [ ] 4.3 创建 `DefinitionRepository` 接口（save、findByName，使用 MachineName 值对象）
- [ ] 4.4 将现有 `InstanceRecord` / `SnapshotRecord` / `DefinitionRecord` 迁移到 domain 层并改名为 `InstanceData` / `SnapshotData` / `DefinitionData`，字段使用值对象类型（`InstanceId`、`StateName`、`InstanceStatus`、`ExecutionStatus`）

## 5. 仓储实现（infrastructure 层）

- [ ] 5.1 创建 `infrastructure.persistence` 包
- [ ] 5.2 实现 `JdbcInstanceRepository`（implements domain.InstanceRepository，复用现有 InstanceRepository SQL 逻辑，每步完成后编译通过）
- [ ] 5.3 实现 `JdbcSnapshotRepository`（implements domain.SnapshotRepository，复用现有 SnapshotRepository SQL 逻辑，移除 updateRouteStatus，每步完成后编译通过）
- [ ] 5.4 实现 `JdbcDefinitionRepository`（implements domain.DefinitionRepository，复用现有 DefinitionRepository SQL 逻辑，每步完成后编译通过）
- [ ] 5.5 移动 `DdlInitializer` 到 infrastructure 包

## 6. 执行引擎瘦身

- [ ] 6.1 从 `StateMachine` 移除所有 Repository 字段（jdbcTemplate、instanceRepository、snapshotRepository、objectMapper、registry）
- [ ] 6.2 从 `StateMachine` 移除 serialize / deserialize 方法
- [ ] 6.3 从 `StateMachine` 移除 execute / retry / resumeByBusinessId / resumeByInstanceId 方法
- [ ] 6.4 `StateMachine` 仅保留：states、transitions、retryPolicy、findState / findNextState / hasOutgoingTransitions / hasState、不可变集合
- [ ] 6.5 转换方法使用 `StateName` 值对象参数
- [ ] 6.6 每步完成后验证编译通过

## 7. Application 层服务

- [ ] 7.1 创建 `application.InstanceExecutionService`（构造器注入 InstanceRepository、SnapshotRepository、ObjectMapper）
- [ ] 7.2 迁移 execute 逻辑到 service（含创建实例、循环调用 StateMachine 转换、快照记录、事务管理）
- [ ] 7.3 迁移 resume 逻辑到 service（含快照恢复、contextMerger 应用、聚合根方法调用）
- [ ] 7.4 迁移 retry 逻辑到 service（含自动恢复上下文、NODE/ROUTE 失败区分、聚合根 recordRetry 调用、路由失败记录新快照）
- [ ] 7.5 编写 application 层单元测试（mock 仓储，验证编排逻辑和聚合根方法调用）

## 8. 向后兼容门面

- [ ] 8.1 创建 `interfaces.StateMachineFacade` 类（持有 StateMachine + InstanceExecutionService，Facade 仅委托给 service，不直接操作聚合根）
- [ ] 8.2 实现 execute/retry/resumeByBusinessId/resumeByInstanceId 兼容方法
- [ ] 8.3 更新 StateMachineAutoConfiguration 装配 StateMachineFacade Bean
- [ ] 8.4 验证 demo 应用无需修改即可通过 Facade 调用

## 9. 包结构重组（渐进式迁移，每步可编译）

- [ ] 9.1 创建空的分层包目录：domain / application / infrastructure / interfaces
- [ ] 9.2 迁移值对象和领域事件到 domain，更新所有引用，验证编译通过
- [ ] 9.3 迁移聚合根基类和 StateMachine 到 domain.engine，更新所有引用，验证编译通过
- [ ] 9.4 迁移 StateMachineInstance 和 ExecutionSnapshot 到 domain，更新所有引用，验证编译通过
- [ ] 9.5 迁移仓储接口到 domain.repository，更新所有引用，验证编译通过
- [ ] 9.6 迁移仓储实现到 infrastructure.persistence，更新所有引用，验证编译通过
- [ ] 9.7 迁移 InstanceExecutionService 到 application，更新所有引用，验证编译通过
- [ ] 9.8 迁移 StateMachineFacade 到 interfaces，更新所有引用，验证编译通过
- [ ] 9.9 迁移 Controller / DTO 到 interfaces.management，更新所有引用，验证编译通过

## 10. AutoConfiguration 适配

- [ ] 10.1 更新 `StateMachineAutoConfiguration` 适配新包路径
- [ ] 10.2 更新 BeanPostProcessor 注入逻辑（注入仓储而非直接注入 JdbcTemplate）
- [ ] 10.3 确保 Bean 装配顺序正确（仓储 → 引擎 → 服务 → 门面）

## 11. 测试适配与验证

- [ ] 11.1 更新单元测试的 import 路径
- [ ] 11.2 更新集成测试的 import 路径
- [ ] 11.3 确保所有 53 个测试通过（`mvn test`）
- [ ] 11.4 运行 demo 应用，验证端到端流程正常
