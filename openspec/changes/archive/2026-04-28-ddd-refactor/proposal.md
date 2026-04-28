## Why

当前项目采用过程式架构，核心引擎（`StateMachine`）、持久化（`Repository`）、管理控制台（`Controller`）混杂在同一层级，缺乏清晰的领域边界。随着功能增长（快照、重试、恢复、路由），`StateMachine` 类已超过 300 行，承担了执行引擎、实例生命周期管理、持久化协调等多重职责。采用 DDD 战术设计可以：

1. **明确聚合边界**：将实例（Instance）、快照（Snapshot）建模为独立聚合，StateMachine 建模为不可变领域对象，各自有自己的不变式
2. **消除贫血模型**：将业务行为（状态转换、重试决策、挂起判断）从过程式方法移入领域对象
3. **隔离基础设施**：通过仓储接口隔离 JDBC 实现，使核心引擎可纯单元测试

## What Changes

- **领域建模**：`StateMachine` 为不可变领域对象（非聚合根，承载转换规则），`StateMachineInstance`（实例聚合根，富行为），`ExecutionSnapshot`（快照聚合根，不可变）
- **仓储接口上移**：将 `InstanceRepository`、`SnapshotRepository`、`DefinitionRepository` 的接口定义移入 `domain` 层，实现留在 `infrastructure` 层
- **执行引擎瘦身**：`StateMachine` 仅保留状态定义、转换规则、重试策略，不持有任何持久化或应用层引用
- **包结构重组**：按 `domain/`、`application/`、`infrastructure/`、`interfaces/` 四层重新组织
- **值对象引入**：`InstanceId`、`SnapshotId`、`DefinitionId`、`BusinessId`、`MachineName`、`StateName` 等用值对象替代裸字符串
- **领域事件**：实例状态变更（`InstanceStarted`、`InstanceCompleted`、`InstanceFailed`、`InstanceSuspended`）通过聚合根收集
- **对外兼容门面**：新增 `StateMachineFacade` 类，持有 StateMachine + InstanceExecutionService，对外保持 execute/retry 签名兼容，避免 domain → application 反向依赖

## Capabilities

### New Capabilities
- `domain-aggregates`: 两个聚合根（Instance + Snapshot，富行为/不可变）、StateMachine 不可变领域对象（转换规则），含值对象、领域事件
- `repository-interfaces`: 仓储接口定义在 domain 层，三个聚合各对应一个 Repository 接口，与基础设施解耦
- `execution-engine`: 纯领域状态转换引擎（StateMachine），不含持久化逻辑
- `instance-lifecycle`: 实例生命周期管理（创建、恢复、重试、挂起）作为 application 层服务
- `value-objects`: 强类型值对象替代裸字符串（InstanceId、DefinitionId、StateName 等），贯穿所有层

### Modified Capabilities
<!-- 无现有 spec，全部为新能力 -->

## Impact

- **包结构**：`cn.chedejun.statemachine.core.*` → 拆分为 `domain/`、`application/`、`infrastructure/`、`interfaces/`
- **StateMachine 类**：大幅瘦身，剥离持久化逻辑，对外 API 由 StateMachineFacade 保持兼容
- **Repository 类**：从直接 JDBC 实现变为 domain 接口 + infrastructure 实现
- **测试**：domain 层可纯单元测试，现有集成测试需适配新包路径
- **向后兼容**：通过 StateMachineFacade 对外 API（Builder、execute/retry 方法）保持不变
