## Context

当前项目是一个 Spring Boot 3 状态机引擎库，采用扁平包结构：`core/`（引擎）、`persistence/`（JDBC 仓储）、`management/`（控制台）、`autoconfigure/`（自动装配）。核心类 `StateMachine` 承担执行引擎、实例管理、持久化协调三重职责，300+ 行代码。仓储类直接依赖 `JdbcTemplate`，无法做纯单元测试。

约束条件：
- 这是一个 library，被其他应用引入，对外 API（Builder + execute/retry）必须保持向后兼容
- 使用 JDBC + 3 张表（definitions / instances / snapshots）
- 已有的 demo 应用和集成测试必须在重构后继续通过

## Goals / Non-Goals

**Goals:**
- 将 `StateMachine` 从 300+ 行瘦到 ~100 行，仅保留状态定义和转换规则
- 领域层（domain）零外部依赖，可纯单元测试
- 两个聚合（StateMachineInstance / ExecutionSnapshot）各自有聚合根、不变式、领域事件、富行为。StateMachine 作为不可变领域对象（不继承 AggregateRoot，不产生事件）承载转换规则
- 仓储接口上移到 domain，JDBC 实现下沉到 infrastructure
- 通过 `StateMachineFacade` 对外保持 Builder API 和 execute/retry 签名不变

**Non-Goals:**
- 不引入 JPA / MyBatis，继续使用 JDBC
- 不做 CQRS，读写路径不变
- 不改变数据库表结构
- 不引入事件总线 / MQ，领域事件仅在 StateMachineInstance 聚合根内收集，由 application 层同步处理。StateMachine 作为不可变领域对象不产生领域事件（注册是启动时一次性操作，非运行时业务行为）
- 不改变 demo 应用的使用方式

## Decisions

### 1. 分层策略：四层包结构

```
cn.chedejun.statemachine/
├── domain/              # 纯领域：聚合根、值对象、领域事件、仓储接口
├── application/         # 编排：InstanceExecutionService（协调 domain + repository）
├── infrastructure/      # 适配：JDBC 仓储实现、DDL 初始化器
├── interfaces/          # 驱动适配器：StateMachineFacade、REST 控制器、DTO
└── autoconfigure/       # 装配：AutoConfiguration、Properties
```

**为什么**：`management/` 重命名为 `interfaces/`，Controller 是驱动适配器（Driver Adapter），符合六边形架构语义。`StateMachineFacade` 也放在 interfaces 层，作为兼容旧 API 的门面。

### 2. 聚合设计：两个聚合根 + StateMachine 不可变领域对象

两个运行时聚合根，一个不可变领域对象：

- **StateMachineInstance**（聚合根）：管理实例生命周期。不变式：状态转换必须合法、重试次数不超过上限。提供富行为方法（`transitionTo()`、`markSuspended()`、`recordRetry()`、`complete()`、`fail()`）。持有 `DefinitionId` 关联 StateMachine 的持久化记录
- **ExecutionSnapshot**（聚合根）：独立聚合，通过 `InstanceId` 与实例关联。快照创建后不可变，路由评估失败记录新快照而非修改旧快照。拥有自己的 `SnapshotRepository`
- **StateMachine**（不可变领域对象，非聚合根）：管理名称、版本、状态列表、转换列表、重试策略。不变式：至少有一个状态、至少有一个初始转换。提供 `findNextState()`、`hasState()` 等转换决策方法。不继承 AggregateRoot，不产生领域事件。

**为什么 StateMachine 不是聚合根**：聚合根是运行时被修改和追踪的业务实体。StateMachine 在应用启动时通过 Builder 一次性构建后不再变化，没有生命周期状态变更，不产生领域事件。它是转换规则的集合，而非业务实体。

**为什么合并**：原有的 `StateMachine` 和 `StateMachineDefinition` 持有相同数据，是同一概念的两个副本。保留 `StateMachine` 因为它已有 Builder API 和用户认知。

### 3. 值对象：轻量 record 实现，贯穿所有层

```java
public record InstanceId(String value) {
    public InstanceId { if (value == null || value.isBlank()) throw ...; }
    public static InstanceId generate() { return new InstanceId(UUID.randomUUID().toString()); }
}
public record DefinitionId(String value) { ... }
public record BusinessId(String value) { ... }
public record MachineName(String value) { ... }
public record StateName(String value) { ... }
```

所有层的领域交互（聚合根方法、仓储接口参数、领域服务参数）统一使用值对象，不再使用裸 String。

**DefinitionId 说明**：`StateMachine` 本身由 (name, version) 在领域层标识，但数据库 `state_machine_definitions` 表使用 UUID 作为主键。`DefinitionId` 是该持久化 UUID 的值对象封装，用于 `StateMachineInstance.definitionId` 外键关联和仓储操作。领域层不依赖 DefinitionId 作为 StateMachine 的标识。

### 4. 领域事件：简单列表收集，同步处理

```java
// AggregateRoot 基类
public abstract class AggregateRoot {
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    protected void addDomainEvent(DomainEvent event) { domainEvents.add(event); }
    public List<DomainEvent> getDomainEvents() { return List.copyOf(domainEvents); }
    public void clearDomainEvents() { domainEvents.clear(); }
}
```

事件：`InstanceStarted`、`InstanceCompleted`、`InstanceFailed`、`InstanceSuspended`。

**为什么**：这是 library，不是分布式系统。领域事件不需要异步发布，仅用于 application 层在事务边界后执行副作用（如写日志）。不需要 Outbox 模式。

### 5. 仓储接口：domain 层，面向聚合

```java
// domain 层
public interface InstanceRepository {
    Optional<InstanceData> findById(InstanceId id);
    InstanceData save(InstanceData instance);
    List<InstanceData> findByMachineName(MachineName name, int offset, int limit);
    int tryMarkRunningFromSuspended(InstanceId id);
    ...
}

public interface SnapshotRepository {
    List<SnapshotData> findByInstanceId(InstanceId id);
    SnapshotData save(SnapshotData snapshot);
    // 路由评估失败记录新快照，不修改旧快照 —— 快照不可变
}

public interface DefinitionRepository {
    void save(DefinitionData definition);
    List<DefinitionData> findByName(MachineName name);
}
```

**为什么**：仓储是领域概念（聚合的持久化契约），定义在 domain 层。一个聚合一个 Repository 接口。

### 6. 聚合富行为：避免贫血模型

```java
// StateMachineInstance 聚合根
public class StateMachineInstance extends AggregateRoot<InstanceId> {
    private DefinitionId definitionId;  // 关联 StateMachine 的持久化 ID
    private StateName currentState;
    private InstanceStatus status;  // RUNNING, SUSPENDED, COMPLETED, FAILED
    private int retryCount;

    public void transitionTo(StateName target, StateMachine<?> machine) {
        validateNotTerminal();
        if (!machine.hasState(target)) throw ...;
        this.currentState = target;
    }

    public void markSuspended() {
        validateTransitionAllowed();
        this.status = InstanceStatus.SUSPENDED;
        addDomainEvent(new InstanceSuspended(this.id));
    }

    public void recordRetry() {
        validateCanRetry();
        this.retryCount++;
    }

    public void complete() { validateNotTerminal(); this.status = InstanceStatus.COMPLETED; addDomainEvent(new InstanceCompleted(this.id)); }
    public void fail(String error) { validateNotTerminal(); this.status = InstanceStatus.FAILED; this.errorMessage = error; addDomainEvent(new InstanceFailed(this.id, error)); }
}
```

**为什么**：聚合根自含校验能力。`StateMachine` 作为转换规则的载体传入，聚合根调用 `hasState()` 验证目标状态合法性，不依赖外部服务。

### 7. 向后兼容：独立 Facade 类

```java
// interfaces 层
public class StateMachineFacade<C> {
    private final StateMachine<C> machine;
    private final InstanceExecutionService<C> executionService;

    public StateMachineFacade(StateMachine<C> machine, InstanceExecutionService<C> service) {
        this.machine = machine;
        this.executionService = service;
    }

    public ExecuteResult execute(C context, String businessId) {
        return executionService.execute(machine, context, BusinessId.ofNullable(businessId));
    }
    public void retry(String instanceId) {
        executionService.retry(machine, InstanceId.of(instanceId));
    }
    public void retry(String instanceId, C context) {
        executionService.retryWithCustomContext(machine, InstanceId.of(instanceId), context);
    }
    public void resumeByBusinessId(String businessId, String expectedState, Consumer<C> merger) {
        executionService.resumeByBusinessId(machine, BusinessId.of(businessId), StateName.of(expectedState), merger);
    }
    public void resumeByInstanceId(String instanceId, String expectedState, Consumer<C> merger) {
        executionService.resumeByInstanceId(machine, InstanceId.of(instanceId), StateName.of(expectedState), merger);
    }
}
```

**为什么**：Facade 在 interfaces 层，同时持有 `StateMachine`（domain）和 `InstanceExecutionService`（application）。Facade 不直接操作聚合根，所有实例生命周期操作都委托给 `InstanceExecutionService`。`StateMachine` 作为参数传入 service 方法，由 service 协调。

### 8. InstanceExecutionService 归属 application 层

`InstanceExecutionService` 负责：创建实例、循环调用 StateMachine 转换、记录快照、序列化/反序列化上下文、事务管理。这些都是编排工作，属于 application 层。

```java
// application 层
public class InstanceExecutionService<C> {
    private final InstanceRepository instanceRepo;
    private final SnapshotRepository snapshotRepo;
    private final ObjectMapper objectMapper;

    public ExecuteResult execute(StateMachine<C> machine, C context, BusinessId businessId) { ... }
    // retry, resume 等方法
}
```

**为什么**：application 层负责协调 domain（StateMachine 转换决策、聚合根状态校验）和 infrastructure（Repository 持久化），这正是它的职责。

### 9. 快照不可变设计

快照一旦创建就不应被修改。`SnapshotRepository` 不提供 update 方法。路由评估失败时，系统 SHALL 创建新的 FAILED 快照而非修改已有的 ROUTE 快照。管理控制台需要查看最新快照时，通过 `findByInstanceId` 按时间排序获取最新记录即可。

**为什么**：快照是执行历史的事实记录，修改历史会破坏审计和排查能力。重试时旧快照仍保留作为参考。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|---|---|
| 重构引入包路径变更导致测试/调用方断裂 | Facade 保持对外 API 不变，仅内部包重排。测试通过 import 更新适配 |
| 两个聚合增加复杂度 | Snapshot 作为独立聚合增加了接口数量，但不可变语义简化了查询逻辑。StateMachine 合并定义与引擎职责，避免双重定义 |
| `StateMachine` 瘦身后与 application 层的循环依赖 | Facade 在 interfaces 层持有双方引用，domain 和 application 之间单向依赖 |
| JDBC 仓储实现迁移后 SQL 行为变化 | 保留现有 SQL，仅包装为接口实现。集成测试覆盖相同场景 |
| 重构跨度大，中途不可用 | 分步实施：先 domain 模型 → 再仓储接口 → 再引擎瘦身 → 最后包重组。每步独立可编译 |
