# Clean架构+DDD+Hexagonal架构技能

<cite>
**本文档引用的文件**
- [README.md](file://README.md)
- [state-machine-boot-starter/README.md](file://state-machine-boot-starter/README.md)
- [InstanceExecutionService.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java)
- [StateMachine.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/engine/StateMachine.java)
- [DefinitionRepository.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/repository/DefinitionRepository.java)
- [AggregateRoot.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/shared/AggregateRoot.java)
- [StateMachineInstance.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/instance/StateMachineInstance.java)
- [JdbcDefinitionRepository.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/infrastructure/persistence/JdbcDefinitionRepository.java)
- [StateMachineFacade.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/interfaces/StateMachineFacade.java)
- [StateMachineBuilder.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/StateMachineBuilder.java)
- [InstanceData.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/data/InstanceData.java)
- [InstanceStartedEvent.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/event/InstanceStartedEvent.java)
- [State.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/State.java)
- [Transition.java](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/Transition.java)
</cite>

## 目录
1. [引言](#引言)
2. [项目结构](#项目结构)
3. [核心组件](#核心组件)
4. [架构概览](#架构概览)
5. [详细组件分析](#详细组件分析)
6. [依赖分析](#依赖分析)
7. [性能考虑](#性能考虑)
8. [故障排除指南](#故障排除指南)
9. [结论](#结论)

## 引言

这是一个基于Clean架构、DDD（领域驱动设计）和Hexagonal架构（六边形架构）的状态机工作流引擎项目。该项目提供了一个完整的状态机解决方案，支持条件分支、步骤快照、失败重试（指数退避）和管理控制台功能。

项目采用分层架构设计，将业务逻辑与基础设施分离，实现了高度的模块化和可测试性。通过使用Spring Boot自动配置机制，开发者可以轻松集成状态机功能到现有的应用程序中。

## 项目结构

项目采用标准的Maven多模块结构，主要包含以下核心模块：

```mermaid
graph TB
subgraph "状态机核心模块"
A[state-machine-boot-starter]
end
subgraph "演示应用"
B[state-machine-demo]
end
subgraph "核心包结构"
C[application/应用层]
D[domain/领域层]
E[infrastructure/基础设施层]
F[interfaces/接口层]
G[persistence/持久化层]
end
A --> C
A --> D
A --> E
A --> F
A --> G
B --> A
```

**图表来源**
- [state-machine-boot-starter/README.md:1-65](file://state-machine-boot-starter/README.md#L1-L65)

### 包层次结构分析

项目按照Clean架构原则组织代码，每个包都有明确的职责分工：

- **application层**：协调用例执行，处理业务流程编排
- **domain层**：包含核心业务逻辑和领域模型
- **infrastructure层**：提供技术实现细节
- **interfaces层**：处理外部接口交互
- **persistence层**：数据持久化相关实现

**章节来源**
- [state-machine-boot-starter/README.md:1-65](file://state-machine-boot-starter/README.md#L1-L65)

## 核心组件

### 状态机引擎核心

状态机引擎是整个系统的核心，负责状态管理和转换逻辑：

```mermaid
classDiagram
class StateMachine {
-String name
-String version
-State[] states
-Transition[] transitions
-RetryPolicy retryPolicy
-Class~C~ contextClass
+findNextState(context, fromState) Optional~String~
+hasOutgoingTransitions(fromState) boolean
+getInitialState() State
}
class State {
-String name
-Action~C~ action
-boolean suspended
+getName() String
+getAction() Action~C~
+isSuspended() boolean
}
class Transition {
-String from
-String to
-Condition~C~ condition
+getFrom() String
+getTo() String
+getCondition() Condition~C~
}
StateMachine --> State : "包含"
StateMachine --> Transition : "包含"
State --> Action : "使用"
```

**图表来源**
- [StateMachine.java:19-77](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/engine/StateMachine.java#L19-L77)
- [State.java:3-23](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/State.java#L3-L23)
- [Transition.java:3-20](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/Transition.java#L3-L20)

### 应用服务层

应用服务层负责协调领域逻辑和基础设施交互：

```mermaid
classDiagram
class InstanceExecutionService {
-InstanceRepository instanceRepo
-SnapshotRepository snapshotRepo
-DefinitionRepository definitionRepo
-ObjectMapper objectMapper
+execute(machine, context, businessId) ExecuteResult
+resumeByBusinessId(machine, businessId, expectedState, merger) void
+retry(machine, instanceId) void
+retryWithCustomContext(machine, instanceId, context) void
-executeLoop(instanceId, context, startState, machine) InstanceData
-executeAction(current, state, context, machine) InstanceData
}
class StateMachineFacade {
-StateMachine~C~ machine
-InstanceExecutionService~C~ executionService
+execute(context, businessId) ExecuteResult
+retry(instanceId) void
+resumeByBusinessId(businessId, expectedState, merger) void
}
InstanceExecutionService --> StateMachine : "使用"
StateMachineFacade --> InstanceExecutionService : "委托"
```

**图表来源**
- [InstanceExecutionService.java:25-296](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L25-L296)
- [StateMachineFacade.java:16-52](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/interfaces/StateMachineFacade.java#L16-L52)

**章节来源**
- [InstanceExecutionService.java:1-296](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L1-L296)
- [StateMachineFacade.java:1-52](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/interfaces/StateMachineFacade.java#L1-L52)

## 架构概览

项目实现了Clean架构的核心原则，通过依赖倒置和分层设计实现了高度的模块化：

```mermaid
graph TB
subgraph "外部层"
UI[用户界面]
API[REST API]
CLI[命令行接口]
end
subgraph "应用层"
APP[应用服务]
FACADE[状态机门面]
end
subgraph "领域层"
DOMAIN[领域模型]
AGGREGATE[聚合根]
EVENT[领域事件]
end
subgraph "基础设施层"
INFRA[基础设施]
REPO[仓库接口]
PERSIST[持久化]
end
UI --> APP
API --> FACADE
CLI --> APP
APP --> DOMAIN
FACADE --> APP
DOMAIN --> REPO
REPO --> PERSIST
PERSIST --> INFRA
```

**图表来源**
- [InstanceExecutionService.java:1-296](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L1-L296)
- [StateMachine.java:1-77](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/engine/StateMachine.java#L1-L77)

### Hexagonal架构实现

项目采用Hexagonal架构模式，通过接口隔离技术实现：

```mermaid
sequenceDiagram
participant Client as 客户端
participant Facade as 状态机门面
participant Service as 应用服务
participant Repo as 仓库接口
participant Impl as 实现类
participant DB as 数据库
Client->>Facade : execute(context, businessId)
Facade->>Service : execute(machine, context, businessId)
Service->>Repo : save(instanceData)
Repo->>Impl : save(instanceData)
Impl->>DB : INSERT/UPDATE
DB-->>Impl : 确认
Impl-->>Repo : 确认
Repo-->>Service : 确认
Service-->>Facade : ExecuteResult
Facade-->>Client : 执行结果
```

**图表来源**
- [StateMachineFacade.java:26-28](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/interfaces/StateMachineFacade.java#L26-L28)
- [InstanceExecutionService.java:43-58](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L43-L58)

## 详细组件分析

### 领域模型分析

领域层包含了状态机的核心概念和业务规则：

```mermaid
classDiagram
class AggregateRoot {
<<abstract>>
-ID id
-DomainEvent[] domainEvents
+id() ID
#addDomainEvent(event) void
+getDomainEvents() DomainEvent[]
#clearDomainEvents() void
}
class StateMachineInstance {
-DefinitionId definitionId
-MachineName machineName
-StateName currentState
-InstanceStatus status
-BusinessId businessId
-int retryCount
-String errorMessage
+transitionTo(target, machine) void
+markSuspended() void
+recordRetry(retryPolicy) void
+complete() void
+fail(error) void
}
class InstanceData {
<<final>>
-InstanceId id
-DefinitionId definitionId
-MachineName machineName
-String definitionVersion
-StateName currentState
-BusinessId businessId
-InstanceStatus status
-int retryCount
-Instant nextRetryAt
-String errorMessage
-Instant createdAt
-Instant updatedAt
+newInstance(...) InstanceData
+withUpdatedState(newState, newStatus, newError) InstanceData
+withIncrementedRetry(newRetryCount, newNextRetryAt) InstanceData
}
AggregateRoot <|-- StateMachineInstance
StateMachineInstance --> InstanceData : "映射"
```

**图表来源**
- [AggregateRoot.java:7-29](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/shared/AggregateRoot.java#L7-L29)
- [StateMachineInstance.java:9-81](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/instance/StateMachineInstance.java#L9-L81)
- [InstanceData.java:8-79](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/data/InstanceData.java#L8-L79)

### 仓库接口设计

项目实现了清晰的仓库模式，将数据访问抽象化：

```mermaid
classDiagram
class DefinitionRepository {
<<interface>>
+save(definition) void
+update(definition) void
+findByNameAndVersion(name, version) Optional~DefinitionData~
+findAllByName(name) DefinitionData[]
+findAll() DefinitionData[]
}
class JdbcDefinitionRepository {
-JdbcTemplate jdbcTemplate
-ObjectMapper objectMapper
-Boolean postgresql
+save(definition) void
+update(definition) void
+findByNameAndVersion(name, version) Optional~DefinitionData~
+findAllByName(name) DefinitionData[]
+findAll() DefinitionData[]
}
DefinitionRepository <|-- JdbcDefinitionRepository
```

**图表来源**
- [DefinitionRepository.java:9-16](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/repository/DefinitionRepository.java#L9-L16)
- [JdbcDefinitionRepository.java:18-119](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/infrastructure/persistence/JdbcDefinitionRepository.java#L18-L119)

### 应用服务执行流程

应用服务实现了复杂的状态机执行逻辑：

```mermaid
flowchart TD
Start([开始执行]) --> Init[初始化实例]
Init --> Loop{执行循环}
Loop --> FindState[查找当前状态]
FindState --> ExecuteAction[执行动作]
ExecuteAction --> Success{执行成功?}
Success --> |是| CheckSuspend{是否挂起?}
Success --> |否| CheckRetry{检查重试次数}
CheckSuspend --> |是| SaveSuspended[保存挂起状态]
CheckSuspend --> |否| NextState[查找下一个状态]
CheckRetry --> |未达上限| WaitDelay[等待延迟]
CheckRetry --> |已达上限| MarkFailed[标记失败]
WaitDelay --> ExecuteAction
MarkFailed --> End([结束])
SaveSuspended --> End
NextState --> HasNext{是否有下一个状态?}
HasNext --> |是| SaveRoute[保存路由快照]
HasNext --> |否| CheckTransitions{检查转换规则}
SaveRoute --> UpdateState[更新状态]
UpdateState --> Loop
CheckTransitions --> |有可用转换| BuildError[构建错误信息]
CheckTransitions --> |无可用转换| Complete[标记完成]
BuildError --> MarkFailed
Complete --> End
```

**图表来源**
- [InstanceExecutionService.java:158-193](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L158-L193)
- [InstanceExecutionService.java:200-237](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L200-L237)

**章节来源**
- [StateMachineInstance.java:1-81](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/instance/StateMachineInstance.java#L1-L81)
- [InstanceExecutionService.java:1-296](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L1-L296)

## 依赖分析

项目遵循Clean架构的依赖规则，确保依赖方向正确：

```mermaid
graph TD
subgraph "外部依赖"
SPRING[Spring Framework]
JDBC[JDBC驱动]
JACKSON[Jackson]
end
subgraph "应用层"
APP[application/*]
end
subgraph "领域层"
DOMAIN[domain/*]
end
subgraph "基础设施层"
INFRA[infrastructure/*]
PERSIST[persistence/*]
end
APP --> DOMAIN
APP -.-> INFRA
DOMAIN -.-> INFRA
INFRA --> PERSIST
PERSIST --> JDBC
APP --> SPRING
DOMAIN --> JACKSON
```

**图表来源**
- [InstanceExecutionService.java:3-17](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L3-L17)
- [JdbcDefinitionRepository.java:6-27](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/infrastructure/persistence/JdbcDefinitionRepository.java#L6-L27)

### 循环依赖检测

项目通过严格的包结构避免了循环依赖：

- **应用层**不依赖基础设施层
- **领域层**不依赖应用层或基础设施层
- **基础设施层**可以依赖领域层（用于数据映射）

**章节来源**
- [AggregateRoot.java:1-29](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/shared/AggregateRoot.java#L1-L29)
- [StateMachine.java:1-77](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/domain/engine/StateMachine.java#L1-L77)

## 性能考虑

### 并发控制和乐观锁

项目实现了高效的并发控制机制：

```mermaid
sequenceDiagram
participant Client1 as 客户端1
participant Client2 as 客户端2
participant Service as 应用服务
participant Repo as 实例仓库
participant DB as 数据库
Client1->>Service : resumeByInstanceId()
Client2->>Service : resumeByInstanceId()
Service->>Repo : tryMarkRunningFromSuspended(id, nextState)
Repo->>DB : UPDATE ... WHERE status='SUSPENDED' AND ROW_VERSION=?
DB-->>Repo : affected_rows=1
Repo-->>Service : updated=1
Note over Client1,DB : 客户端1成功获取锁
Service->>Repo : tryMarkRunningFromSuspended(id, nextState)
Repo->>DB : UPDATE ... WHERE status='SUSPENDED' AND ROW_VERSION=?
DB-->>Repo : affected_rows=0
Repo-->>Service : updated=0
Note over Client2,DB : 客户端2获取锁失败
```

**图表来源**
- [InstanceExecutionService.java:139-142](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L139-L142)

### 缓存策略

项目采用了智能的缓存策略来优化性能：

- **状态机定义缓存**：通过版本号和名称索引
- **实例状态缓存**：最近使用的实例状态
- **快照数据缓存**：成功执行的上下文数据

### 错误处理和重试机制

实现了完善的错误处理和重试策略：

- **指数退避算法**：避免雪崩效应
- **最大重试次数限制**：防止无限重试
- **超时控制**：避免长时间阻塞

## 故障排除指南

### 常见问题诊断

#### 状态机执行异常

当状态机执行出现异常时，系统会记录详细的错误信息：

1. **状态不存在**：检查状态名称拼写和定义
2. **转换条件失败**：验证条件函数逻辑
3. **重试次数耗尽**：检查系统资源和外部依赖

#### 数据一致性问题

```mermaid
flowchart TD
Error[检测到不一致] --> LogError[记录详细日志]
LogError --> CheckSnapshots[检查快照完整性]
CheckSnapshots --> FixData[修复数据不一致]
FixData --> VerifyConsistency[验证一致性]
VerifyConsistency --> Success{修复成功?}
Success --> |是| Continue[继续执行]
Success --> |否| ManualIntervention[人工干预]
ManualIntervention --> End([结束])
Continue --> End
```

#### 性能问题排查

1. **数据库连接池监控**：检查连接数和等待时间
2. **内存使用情况**：监控堆内存和GC频率
3. **网络延迟**：检查外部服务响应时间

**章节来源**
- [InstanceExecutionService.java:214-236](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java#L214-L236)
- [JdbcDefinitionRepository.java:95-106](file://state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/infrastructure/persistence/JdbcDefinitionRepository.java#L95-L106)

## 结论

本项目成功实现了Clean架构、DDD和Hexagonal架构的最佳实践，提供了以下核心价值：

### 架构优势

1. **高度模块化**：清晰的分层设计使得代码易于维护和扩展
2. **业务逻辑独立**：领域模型不受技术细节影响
3. **测试友好**：依赖注入和接口抽象便于单元测试
4. **可扩展性强**：新的状态机可以在不影响现有代码的情况下添加

### 技术亮点

1. **智能重试机制**：实现了指数退避和最大重试次数控制
2. **快照持久化**：完整记录执行过程，支持故障恢复
3. **并发安全**：通过乐观锁保证数据一致性
4. **灵活的配置**：支持多种数据库和部署环境

### 应用场景

该状态机引擎适用于以下场景：

- **工作流自动化**：订单处理、审批流程等
- **业务流程编排**：复杂的多步骤业务操作
- **微服务协调**：跨服务的状态同步和协调
- **任务调度系统**：定时任务和批处理作业

通过遵循Clean架构原则，项目不仅实现了功能需求，更重要的是建立了可持续发展的技术基础，为未来的功能扩展和技术演进奠定了坚实的基础。