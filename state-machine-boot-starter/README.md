# State Machine Ext

Spring Boot 状态机工作流工具，支持条件分支、步骤快照、失败重试（指数退避）和管理控制台。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>cn.chedejun</groupId>
    <artifactId>state-machine-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义状态机

```java
@Configuration
public class OrderConfig {
    @Bean
    public StateMachine<OrderContext> orderMachine() {
        return StateMachine.<OrderContext>builder("order-process")
            .state("check-inventory", OrderActions::checkInventory)
            .state("pay", OrderActions::processPayment)
            .state("ship", OrderActions::processShipping)
            .transition("check-inventory", "pay", ctx -> ctx.getStock() > 0)
            .transition("check-inventory", "notify-shortage", ctx -> ctx.getStock() <= 0)
            .transition("pay", "ship", ctx -> true)
            .retryPolicy(RetryPolicy.exponentialBackoff()
                .maxAttempts(3)
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(30, TimeUnit.SECONDS))
            .build();
    }
}
```

### 3. 执行

```java
@Autowired
private StateMachine<OrderContext> orderMachine;
String instanceId = orderMachine.execute(ctx);

// 失败重试（需要传入新的 context）
orderMachine.retry(instanceId, newContext);
```

### 4. 管理控制台

访问 `http://localhost:8080/statemachine/` 查看流程图、实例列表和执行快照。

## 配置

```yaml
state-machine:
  ddl-auto: update
  management:
    enabled: true
  console:
    enabled: true
```
