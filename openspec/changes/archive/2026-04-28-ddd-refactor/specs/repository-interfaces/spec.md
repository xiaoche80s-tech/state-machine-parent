## ADDED Requirements

### Requirement: 仓储接口定义在 domain 层

系统 SHALL 在 `domain.repository` 包下定义 `InstanceRepository`、`SnapshotRepository`、`DefinitionRepository` 接口。接口不包含任何 Spring / JDBC 框架依赖，仅使用领域类型（值对象、聚合根）作为方法签名。

#### Scenario: 接口无框架依赖
- **WHEN** 编译 domain 模块
- **THEN** domain 模块 SHALL 不依赖 spring-jdbc、spring-web 等外部库

#### Scenario: 接口使用值对象参数
- **WHEN** 检查仓储接口方法签名
- **THEN** SHALL 使用 `InstanceId`、`MachineName`、`BusinessId`、`StateName` 等值对象，而非裸 String

### Requirement: InstanceRepository 接口

系统 SHALL 提供 `InstanceRepository` 接口，面向 `StateMachineInstance` 聚合，支持按 ID 查找、按业务 ID 查找、保存（创建/更新）、多条件过滤查询、原子状态转换。

#### Scenario: 按 ID 查找
- **WHEN** 调用 `findById(InstanceId)`
- **THEN** 返回 Optional<InstanceData>，不存在时返回 empty

#### Scenario: 按业务 ID 查找
- **WHEN** 调用 `findByBusinessId(MachineName, BusinessId)`
- **THEN** 返回该状态机下最新创建的实例

#### Scenario: 保存实例
- **WHEN** 调用 `save(InstanceData)`
- **THEN** 新记录插入数据库，已存在记录更新

#### Scenario: 原子状态转换
- **WHEN** 调用 `tryMarkRunningFromSuspended(InstanceId)`
- **THEN** 仅当当前状态为 SUSPENDED 时才更新为 RUNNING，返回受影响的行数

### Requirement: SnapshotRepository 接口

系统 SHALL 提供 `SnapshotRepository` 接口，面向 `ExecutionSnapshot` 独立聚合，支持按实例 ID 查询、保存快照。快照不可变，不提供更新方法；路由评估失败记录新快照而非修改旧快照。

#### Scenario: 查询实例所有快照
- **WHEN** 调用 `findByInstanceId(InstanceId)`
- **THEN** 按执行时间顺序返回快照列表

#### Scenario: 保存执行快照
- **WHEN** 调用 `save(SnapshotData)`
- **THEN** 新快照记录插入数据库

#### Scenario: 无更新方法
- **WHEN** 检查 SnapshotRepository 接口
- **THEN** SHALL 不包含任何 update 或 modify 方法，保持快照不可变语义

### Requirement: DefinitionRepository 接口

系统 SHALL 提供 `DefinitionRepository` 接口，面向 `StateMachine` 聚合，支持定义持久化、按名称查询所有版本。

#### Scenario: 持久化定义
- **WHEN** 调用 `save(DefinitionData)`
- **THEN** 定义记录插入或更新到数据库

#### Scenario: 按名称查版本
- **WHEN** 调用 `findByName(MachineName)`
- **THEN** 返回该名称下所有版本的定义列表

### Requirement: 仓储实现在 infrastructure 层

系统 SHALL 在 `infrastructure.persistence` 包下提供基于 `JdbcTemplate` 的仓储实现类，实现 domain 层定义的接口。

#### Scenario: 实现依赖 JDBC
- **WHEN** 编译 infrastructure 模块
- **THEN** infrastructure 模块 SHALL 依赖 spring-jdbc

#### Scenario: 实现 domain 接口
- **WHEN** 检查仓储实现类
- **THEN** SHALL `implements` domain 层对应的 Repository 接口
