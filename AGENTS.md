# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目概述

`cn.chedejun.statemachine` 是一个 Spring Boot Starter 库，提供可嵌入的状态机引擎。支持条件分支、步骤快照、失败重试（指数退避）、挂起/恢复、Web 控制台（Vue3 + Mermaid.js 可视化）。

## 常用命令

```bash
mvn clean install           # 构建全部模块
mvn test                    # 运行全部测试
mvn test -Dtest=XXX         # 运行单个测试（如 StateMachineBuilderTest）
mvn test -Dtest=*IntegrationTest  # 运行集成测试
cd state-machine-demo && mvn spring-boot:run  # 启动示例应用
```

这是一个 library 项目，不直接运行，由宿主 Spring Boot 应用引用后启动。`state-machine-demo/` 是一个可独立运行的示例应用。

## 技术栈

- **JDK 8** + Spring Boot 2.7.18
- Maven 构建
- Jackson（JSON 序列化/反序列化上下文快照）
- JDBC（可选持久化，支持 H2/MySQL/PostgreSQL）
- H2（测试数据库）+ PostgreSQL（集成测试）
- Vue3 + Mermaid.js（Web 控制台，静态资源）

## 架构概览

项目采用 DDD 分层架构：

```
state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/
├── core/                           # 核心接口与值对象
│   ├── Action / Condition          # 函数式接口
│   ├── Context                     # 通用键值对上下文
│   ├── StateMachineBuilder          # Fluent Builder API
│   ├── StateMachineRegistry         # 状态机定义注册 + 持久化
│   ├── State / Transition           # 状态与转换定义
│   ├── RetryPolicy                  # 重试策略（指数退避）
│   └── StateMachineException        # 统一异常
├── domain/                         # 领域层
│   ├── engine/StateMachine          # 不可变领域对象（状态定义 + 转换规则 + 重试策略）
│   ├── instance/StateMachineInstance # 聚合根（运行时实例生命周期）
│   ├── snapshot/ExecutionSnapshot    # 快照工厂方法
│   ├── shared/                      # 值对象 + 领域事件
│   │   ├── AggregateRoot             # 聚合根基类
│   │   ├── *Id (InstanceId, StateName, ...) # 强类型 ID
│   │   ├── InstanceStatus / ExecutionStatus # 状态枚举
│   │   └── DomainEvent + 事件子类      # 领域事件
│   ├── data/                        # 数据对象（DTO）
│   └── repository/                  # 仓储接口
├── application/
│   └── InstanceExecutionService      # 应用服务（执行循环、重试、恢复）
├── infrastructure/persistence/       # JDBC 仓储实现
│   ├── JdbcDefinitionRepository
│   ├── JdbcInstanceRepository
│   └── JdbcSnapshotRepository
├── interfaces/
│   └── StateMachineFacade            # 对外 API 门面（execute/retry/resume）
├── management/                       # Web 控制台 + Actuator 端点
│   ├── ConsoleController
│   ├── StateMachineEndpoint
│   └── dto/
└── autoconfigure/
    ├── StateMachineAutoConfiguration # Spring Boot 自动配置入口
    └── StateMachineProperties        # 配置属性（ddl-auto 等）
```

**关键设计模式：**
- Builder Pattern：`StateMachineBuilder.<C>builder(name)` 链式构建
- Strategy Pattern：`Action<C>` / `Condition<C>` 函数式接口
- Auto-Configuration：Spring Boot 条件自动装配（`spring.factories`）
- Repository Pattern：JDBC 数据库访问抽象
- Facade Pattern：`StateMachineFacade` 封装内部复杂性
- BeanPostProcessor：自动拦截 `StateMachineBuilder` Bean，注入 JdbcTemplate 和 Registry

## 使用方式

### 1. 定义状态机

```java
@Bean
public StateMachineFacade<OrderContext> orderMachine(
        InstanceExecutionService<OrderContext> executionService,
        StateMachineRegistry registry) {
    StateMachine<OrderContext> machine = StateMachineBuilder.<OrderContext>builder("order-process")
        .contextClass(OrderContext.class)
        .state("check-inventory", this::checkInventory)
        .state("process-payment", this::processPayment)
        .suspendState("await-ship-confirm", this::awaitShipConfirm)  // 挂起点
        .transition("check-inventory", "process-payment", ctx -> ctx.getStock() > 0)
        .transition("process-payment", "await-ship-confirm", ctx -> ctx.isPaymentSuccess())
        .retryPolicy(RetryPolicy.exponentialBackoff()
            .maxAttempts(3)
            .initialDelay(1, TimeUnit.SECONDS)
            .build())
        .build();
    registry.register(machine);
    return new StateMachineFacade<>(machine, executionService);
}
```

### 2. 执行

```java
@Autowired StateMachineFacade<OrderContext> orderMachine;

ExecuteResult result = orderMachine.execute(context, businessId);
orderMachine.retry(instanceId);                             // 从快照恢复上下文重试
orderMachine.retry(instanceId, newContext);                 // 使用新上下文重试
orderMachine.resumeByBusinessId(businessId, "await-ship-confirm", ctx -> { ... });  // 恢复挂起
```

### 执行流程

1. `execute()` → 创建实例记录 → 进入 `executeLoop()`
2. 按状态顺序查找匹配的 Transition，条件满足则转换
3. 每个状态执行 Action，成功/失败都记录快照（含输入输出 JSON）
4. 失败时按 RetryPolicy 指数退避重试，超过最大次数标记 FAILED
5. `suspendState` 标记为 SUSPENDED，可通过 `resumeByBusinessId` / `resumeByInstanceId` 恢复
6. 无匹配 Transition 时标记 COMPLETED

## 配置属性

```yaml
state-machine:
  ddl-auto: update           # DDL 自动更新策略（update/none）
  management:
    enabled: true            # Actuator 端点开关
  console:
    enabled: true            # Web 控制台开关
```

自动配置入口：`META-INF/spring.factories`

## 数据库

3 张核心表：
- `state_machine_definitions` — 状态机定义（JSONB 存储状态/转换/重试策略）
- `state_machine_instances` — 运行实例（状态、重试计数）
- `state_machine_snapshots` — 执行快照（输入/输出 JSON、尝试次数）

DDL 脚本位于 `src/main/resources/ddl/`，支持 H2、MySQL、PostgreSQL。`DdlInitializer` 自动检测数据库类型并加载对应脚本。

## 关键类关系

- `StateMachineAutoConfiguration` 通过 `BeanPostProcessor` 拦截 `StateMachineBuilder` Bean，注入 JdbcTemplate 和 Registry
- `StateMachineBeanRegistrar` 自动注册所有 `StateMachine` Bean 到 Registry
- `StateMachineFacade` 是对外 API，委托给 `InstanceExecutionService`
- `InstanceExecutionService.executeLoop()` 是核心执行循环
- 快照的 inputJson/outputJson 用于重试时恢复上下文
- `resumeByBusinessId` 使用 CAS 原子 SQL（`tryMarkRunningFromSuspended`）避免并发恢复冲突

## demo 项目

`state-machine-demo/` 是一个完整的 Spring Boot 示例应用：
- `OrderConfig` — 定义订单状态机（库存检查→支付→挂起→发货→通知）
- `OutboundConfig` — 另一个状态机示例
- `DemoController` — REST API 触发状态机
- 可直接 `cd state-machine-demo && mvn spring-boot:run` 运行

## 开发注意事项

- 可选依赖：`spring-boot-starter-web`、`spring-boot-starter-actuator`、`spring-boot-starter-jdbc` 均为 optional，宿主应用可按需引入
- 所有回复使用中文
- 代码注释和提交信息使用中文
- 当前分支 `jdk8` 已降级至 JDK 8 兼容性，使用 Spring Boot 2.7.18
