# 状态机挂起/恢复功能设计

> 日期: 2026-04-23
> 状态: 待审核

## 需求概述

1. 状态机需要关联业务系统标识 `businessId`，同一 `definitionId` + `businessId` 组合唯一
2. 状态机执行到指定 State 后可挂起（不继续执行），等待外部唤醒
3. 提供 `resumeByBusinessId` 和 `resumeByInstanceId` 方法唤醒挂起的实例，支持在回调中修改 context
4. 挂起点在流程定义中明确标记，前端可识别挂起点节点

## DDL 变更

### state_machine_instances 表

```sql
ALTER TABLE state_machine_instances
  ADD COLUMN business_id VARCHAR(128) NULL;
```

- `business_id`：业务系统标识，同一状态机定义下唯一
- 允许 NULL，保持向后兼容

### 状态枚举值扩展

现有 `status` 枚举值：`RUNNING` / `COMPLETED` / `FAILED`
新增：`SUSPENDED` — 实例在挂起点暂停，等待外部唤醒

删除：`REACHED` — 该状态与已移除的 `targetState` 参数绑定

### 三套 DDL 同步更新（H2/MySQL/PostgreSQL）

在 `state_machine_instances` 的建表语句中增加 `business_id VARCHAR(128)` 列定义。

## 核心类改动

### 1. State<C> — 增加 suspended 字段

`State` 增加 `suspended` 布尔字段，标记该状态是否为挂起点。该属性在构造时确定，不可变。

```java
public class State<C> {
    private final String name;
    private final Action<C> action;
    private final boolean suspended;

    public State(String name, Action<C> action, boolean suspended) { ... }
    public boolean isSuspended() { return suspended; }
}
```

### 2. StateMachineBuilder<C> — 新增 suspendState()

Builder 增加 `suspendState(name, action)` 方法，构造 `suspended = true` 的 State。
现有的 `state(name, action)` 方法内部构造 `suspended = false` 的 State。

```java
public StateMachineBuilder<C> suspendState(String name, Action<C> action) {
    states.add(new State<>(name, action, true));
    return this;
}
```

挂起点信息随 states JSON 序列化到 `state_machine_definitions` 表中，定义持久化时自动携带。

### 3. StateMachine<C> — 简化 execute，新增恢复方法

**移除 `execute(context, targetState)` 和 `execute(context, startState, targetState)` 方法。** `StateMachine` 只保留一个执行入口：

```java
public ExecuteResult execute(C context)
```

`executeLoop` 签名相应简化，不再需要 `startState` 和 `targetState` 参数。

终止逻辑：
- 无下一状态 → `COMPLETED`
- 遇到挂起点 → `SUSPENDED`

由于 `REACHED` 状态与 `targetState` 绑定，移除 `targetState` 后一并删除 `REACHED` 状态。

新增两个恢复方法：

```java
/** 通过业务 ID 恢复挂起的实例 */
public void resumeByBusinessId(String businessId, Consumer<C> contextMerger)

/** 通过状态机实例 ID 恢复挂起的实例 */
public void resumeByInstanceId(String instanceId, Consumer<C> contextMerger)
```

两个方法内部共享同一恢复流程：
1. 查找 SUSPENDED 状态的实例
2. 从最新快照恢复 context（Jackson 反序列化）
3. 调用 `contextMerger.accept(context)` 修改 context
4. 将实例状态更新为 `RUNNING`
5. 调用 `executeLoop` 从挂起状态的下一个状态继续执行

### 4. executeLoop — 挂起逻辑

在状态 Action 执行成功后、查找 nextState 之前，检查当前 State 是否为挂起点：

```java
if (state.isSuspended()) {
    instanceRepository.markSuspended(instanceId, currentState);
    return;  // 停止执行循环，不继续转换到下一状态
}
```

### 5. InstanceRepository — 新增方法

- `findByBusinessId(String definitionId, String businessId)` — 查找指定业务 ID 的实例
- `markSuspended(String id, String currentState)` — 标记实例为 SUSPENDED 状态
- `create` 方法签名增加 `businessId` 参数

### 6. StateMachineAutoConfiguration — 无改动

BeanPostProcessor 逻辑不变，自动注入不受影响。

### 7. ExecuteResult — 增加 businessId 字段

返回结果中携带 businessId，便于调用方追踪。

## 前端标识

`ConsoleController` 返回状态列表时，每个 State 携带 `suspended` 布尔属性。前端读取定义数据后可据此对挂起点节点做特殊标识（如红色边框、暂停图标等）。

## 使用示例

```java
@Bean
public StateMachine<OrderContext> orderMachine() {
    return StateMachineBuilder.<OrderContext>builder("order-process")
        .contextClass(OrderContext.class)
        .state("check-inventory", this::checkInventory)
        .state("process-payment", this::processPayment)
        .suspendState("wait-approval", ctx -> {})  // 挂起点：等待审批
        .state("ship-order", this::shipOrder)
        .transition("check-inventory", "process-payment", ctx -> ctx.getStock() > 0)
        .transition("process-payment", "wait-approval", ctx -> ctx.getPaymentSuccess())
        .transition("wait-approval", "ship-order", ctx -> ctx.isApproved())
        .build();
}

// 执行状态机
String instanceId = machine.execute(context);

// 恢复（通过 businessId）
machine.resumeByBusinessId("ORDER-20260423-001", ctx -> ctx.put("approved", true));

// 恢复（通过 instanceId）
machine.resumeByInstanceId("some-uuid", ctx -> ctx.setApproved(true));
```
