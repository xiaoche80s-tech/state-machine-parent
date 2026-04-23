# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`cn.chedejun.statemachine` 是一个 Spring Boot 3 Starter 库，提供可嵌入的状态机引擎。支持条件分支、步骤快照、失败重试（指数退靠）、Web 控制台（Vue3 + Mermaid.js 可视化）。

## 常用命令

```bash
mvn clean install           # 构建
mvn test                    # 运行全部测试
mvn test -Dtest=XXX         # 运行单个测试
mvn test -Dtest=*IntegrationTest  # 运行集成测试
```

这是一个 library 项目，不直接运行，由宿主 Spring Boot 应用引用后启动。`demo/` 目录是一个可独立运行的示例应用。

## 技术栈

- Java 17 + Spring Boot 3.2.5
- Maven 构建
- Jackson（JSON 序列化/反序列化上下文快照）
- JDBC（可选持久化，支持 H2/MySQL/PostgreSQL）
- H2（测试数据库）+ PostgreSQL（集成测试）
- Vue3 + Mermaid.js（Web 控制台，静态资源）

## 架构概览

```
src/main/java/cn/chedejun/statemachine/
├── autoconfigure/                    # Spring Boot 自动配置
│   ├── StateMachineAutoConfiguration # 条件自动装配入口（BeanPostProcessor 注入）
│   └── StateMachineProperties        # 配置属性（ddl-auto 等）
├── core/                             # 核心状态机引擎
│   ├── StateMachine                  # 主执行引擎（execute/retry/executeLoop）
│   ├── StateMachineBuilder            # Fluent Builder API
│   ├── StateMachineRegistry           # 状态机注册表 + 定义持久化
│   ├── State                          # 状态定义（封装 Action）
│   ├── Transition                     # 状态转换（封装 Condition）
│   ├── Action                         # 函数式接口：void execute(C)
│   ├── Condition                      # 函数式接口：boolean test(C)
│   ├── Context                        # 通用键值对上下文基类
│   ├── RetryPolicy                    # 重试策略（指数退靠 Builder）
│   └── StateMachineException          # 统一异常
├── management/                        # 管理功能
│   ├── ConsoleController              # Web 控制台 REST API
│   ├── StateMachineEndpoint           # Actuator 端点
│   └── dto/                           # 数据传输对象
└── persistence/                       # 数据库持久层
    ├── DefinitionRepository            # 定义 JSONB 存储
    ├── InstanceRepository              # 实例状态存储
    ├── SnapshotRepository              # 步骤快照存储
    └── DdlInitializer                  # DDL 初始化器（自动检测 DB 类型）
```

**关键设计模式：**
- Builder Pattern：`StateMachineBuilder.<C>builder(name)` 链式构建
- Strategy Pattern：`Action<C>` / `Condition<C>` 函数式接口
- Auto-Configuration：Spring Boot 条件自动装配（`@ConditionalOnXxx`）
- Repository Pattern：JDBC 数据库访问抽象
- BeanPostProcessor：自动拦截所有 `StateMachine` Bean 注入 JDBC + 注册

## 使用方式

### 1. 定义自定义 Context 类

继承或不继承均可。推荐使用具体类（便于 Jackson 反序列化重试恢复）：

```java
public class OrderContext {
    private String orderId;
    private int stock;
    private boolean paymentSuccess;
    // getters/setters
}
```

也可以使用内置的 `Context`（键值对模式）：`context.put("key", value)`

### 2. 通过 Builder 定义状态机

```java
@Bean
public StateMachine<OrderContext> orderMachine() {
    return StateMachineBuilder.<OrderContext>builder("order-process")
        .contextClass(OrderContext.class)
        .state("check-inventory", this::checkInventory)
        .state("process-payment", this::processPayment)
        .transition("check-inventory", "process-payment", ctx -> ctx.getStock() > 0)
        .retryPolicy(RetryPolicy.exponentialBackoff()
            .maxAttempts(3)
            .initialDelay(1, TimeUnit.SECONDS)
            .build())
        .build();
}
```

### 3. 执行

```java
@Autowired StateMachine<OrderContext> machine;

String instanceId = machine.execute(context);          // 从头执行
String instanceId = machine.execute(context, "startState", "targetState");  // 从指定状态到指定状态
machine.retry(instanceId);                             // 重试失败实例（自动从快照恢复 context）
machine.retry(instanceId, context);                    // 重试失败实例（使用新 context）
```

### 执行流程

1. `execute()` → 创建实例记录 → 进入 `executeLoop()`
2. 按状态顺序查找匹配的 Transition，条件满足则转换
3. 每个状态执行 Action，成功/失败都记录快照（含输入输出 JSON）
4. 失败时按 RetryPolicy 指数退靠重试，超过最大次数标记 FAILED
5. 无匹配 Transition 时标记 COMPLETED

## 配置属性

```yaml
state-machine:
  ddl-auto: update           # DDL 自动更新策略（update/none）
  management:
    enabled: true            # Actuator 端点开关
  console:
    enabled: true            # Web 控制台开关
```

自动配置入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 数据库

3 张核心表：
- `state_machine_definitions` — 状态机定义（JSONB 存储状态/转换/重试策略）
- `state_machine_instances` — 运行实例（状态、重试计数）
- `state_machine_snapshots` — 执行快照（输入/输出 JSON、尝试次数）

DDL 脚本位于 `src/main/resources/ddl/`，支持 H2、MySQL、PostgreSQL。`DdlInitializer` 自动检测数据库类型并加载对应脚本。

## 关键类关系

- `StateMachineAutoConfiguration` 通过 `BeanPostProcessor` 自动拦截所有 `StateMachine` Bean，注入 `JdbcTemplate` 和 `Registry`
- `StateMachineRegistry` 在注册时将状态机定义持久化到数据库（JSON 格式）
- `StateMachine.execute()` 和 `StateMachine.retry()` 共享 `executeLoop()` 核心循环
- 快照的 inputJson/outputJson 用于重试时恢复上下文

## demo 项目

`demo/` 是一个完整的 Spring Boot 示例应用：
- `OrderConfig` — 定义订单状态机（库存检查→支付→发货→通知）
- `OrderContext` — 订单上下文（orderId、stock、amount、paymentSuccess）
- `DemoController` — REST API 触发状态机
- 可直接 `cd demo && mvn spring-boot:run` 运行

## 开发注意事项

- 可选依赖：`spring-boot-starter-web`、`spring-boot-starter-actuator`、`spring-boot-starter-jdbc` 均为 optional，宿主应用可按需引入
- `StateMachineBuilder` 的 `jdbcTemplate()` 和 `registry()` 方法是包级私有，由 `BeanPostProcessor` 自动调用
- 所有回复使用中文
- 代码注释和提交信息使用中文
