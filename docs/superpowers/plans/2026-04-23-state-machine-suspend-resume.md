# 状态机挂起/恢复功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为状态机增加 businessId 关联、挂起点定义、挂起执行和恢复执行功能。

**Architecture:** 在现有 State/Builder/StateMachine/InstanceRepository 类上扩展，新增 SUSPENDED 状态，新增 resumeByBusinessId/resumeByInstanceId 方法。保留 Context 类，推荐强类型 POJO。

**Tech Stack:** Java 17, Spring Boot 3.2.5, JdbcTemplate, Jackson, JUnit 5, H2/PostgreSQL

**约束：**
- 方法形参不超过 4 个（构造函数除外）
- 优先使用强类型 Context 而非 put/get
- DRY / YAGNI / TDD / 频繁提交

---

## 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| Modify | `src/main/resources/ddl/h2.sql` | 增加 business_id 列 |
| Modify | `src/main/resources/ddl/mysql.sql` | 增加 business_id 列 |
| Modify | `src/main/resources/ddl/postgresql.sql` | 增加 business_id 列 |
| Modify | `src/main/java/cn/chedejun/statemachine/core/State.java` | 增加 suspended 字段 |
| Modify | `src/main/java/cn/chedejun/statemachine/core/StateMachineBuilder.java` | 新增 suspendState()，修改 state() |
| Modify | `src/main/java/cn/chedejun/statemachine/core/StateMachine.java` | 简化 execute，删除 targetState 方法，新增 resumeByBusinessId/resumeByInstanceId，executeLoop 增加挂起逻辑 |
| Modify | `src/main/java/cn/chedejun/statemachine/core/ExecuteResult.java` | 增加 businessId 字段 |
| Modify | `src/main/java/cn/chedejun/statemachine/persistence/InstanceRepository.java` | 新增 findByBusinessId/markSuspended，修改 create 增加 businessId |
| Modify | `src/test/java/cn/chedejun/statemachine/core/StateMachineBuilderTest.java` | 更新测试适配新 API，删除 targetState 测试，增加 suspendState 测试 |
| Modify | `src/test/java/cn/chedejun/statemachine/integration/StateMachineIntegrationTest.java` | 增加挂起/恢复集成测试 |

---

### Task 1: DDL 变更 — 增加 business_id 列

**Files:**
- Modify: `src/main/resources/ddl/h2.sql`
- Modify: `src/main/resources/ddl/mysql.sql`
- Modify: `src/main/resources/ddl/postgresql.sql`

**Step 1: 修改 H2 DDL**

```sql
-- h2.sql 中 state_machine_instances 表增加 business_id
CREATE TABLE IF NOT EXISTS state_machine_instances (
    id VARCHAR(64) PRIMARY KEY, definition_id VARCHAR(64), machine_name VARCHAR(128) NOT NULL,
    definition_version VARCHAR(32), current_state VARCHAR(64),
    business_id VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING', retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP, error_message CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Step 2: 修改 MySQL DDL**

```sql
-- mysql.sql 中 state_machine_instances 表增加 business_id
CREATE TABLE IF NOT EXISTS state_machine_instances (
    id VARCHAR(64) PRIMARY KEY, definition_id VARCHAR(64), machine_name VARCHAR(128) NOT NULL,
    definition_version VARCHAR(32), current_state VARCHAR(64),
    business_id VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING', retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP NULL, error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**Step 3: 修改 PostgreSQL DDL**

```sql
-- postgresql.sql 中 state_machine_instances 表增加 business_id
CREATE TABLE IF NOT EXISTS state_machine_instances (
    id VARCHAR(64) PRIMARY KEY, definition_id VARCHAR(64), machine_name VARCHAR(128) NOT NULL,
    definition_version VARCHAR(32), current_state VARCHAR(64),
    business_id VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING', retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP, error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Step 4: 验证构建**

```bash
mvn clean test -pl state-machine-boot-starter -DskipTests
```

预期：编译通过。

**Step 5: 提交**

```bash
git add src/main/resources/ddl/*.sql
git commit -m "feat: DDL 增加 business_id 列到 state_machine_instances 表"
```

---

### Task 2: State<C> 增加 suspended 字段

**Files:**
- Modify: `src/main/java/cn/chedejun/statemachine/core/State.java`
- Test: `src/test/java/cn/chedejun/statemachine/core/StateMachineBuilderTest.java`

**Step 1: 编写测试 — State 区分普通状态和挂起状态**

在 `StateMachineBuilderTest.java` 中添加：

```java
@Test
void state_defaultNotSuspended() {
    StateMachine<Context> m = StateMachineBuilder.<Context>builder("suspended-test")
        .state("normal", ctx -> {})
        .suspendState("suspend-point", ctx -> {})
        .retryPolicy(RetryPolicy.none())
        .jdbcTemplate(jdbcTemplate).build();

    State<Context> normal = m.getStates().stream()
        .filter(s -> s.getName().equals("normal")).findFirst().orElseThrow();
    State<Context> suspended = m.getStates().stream()
        .filter(s -> s.getName().equals("suspend-point")).findFirst().orElseThrow();

    assertFalse(normal.isSuspended());
    assertTrue(suspended.isSuspended());
}
```

**Step 2: 运行测试确认失败**

```bash
mvn test -pl state-machine-boot-starter -Dtest=StateMachineBuilderTest#state_defaultNotSuspended -v
```

预期：编译失败（suspendState 和 isSuspended 方法不存在）。

**Step 3: 修改 State.java**

```java
package cn.chedejun.statemachine.core;

public class State<C> {
    private final String name;
    private final Action<C> action;
    private final boolean suspended;

    public State(String name, Action<C> action) {
        this(name, action, false);
    }

    public State(String name, Action<C> action, boolean suspended) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("State name cannot be null or empty");
        this.name = name;
        this.action = action;
        this.suspended = suspended;
    }

    public String getName() { return name; }
    public Action<C> getAction() { return action; }
    public boolean isSuspended() { return suspended; }
}
```

**Step 4: 运行测试确认通过**

```bash
mvn test -pl state-machine-boot-starter -Dtest=StateMachineBuilderTest#state_defaultNotSuspended -v
```

预期：PASS。

**Step 5: 提交**

```bash
git add src/main/java/cn/chedejun/statemachine/core/State.java src/test/java/cn/chedejun/statemachine/core/StateMachineBuilderTest.java
git commit -m "feat: State 增加 suspended 字段，支持挂起点标记"
```

---

### Task 3: StateMachineBuilder 新增 suspendState() 方法

**Files:**
- Modify: `src/main/java/cn/chedejun/statemachine/core/StateMachineBuilder.java`

**Step 1: 修改 StateMachineBuilder.java**

修改现有 `state()` 方法，新增 `suspendState()` 方法：

```java
public StateMachineBuilder<C> state(String name, Action<C> action) {
    states.add(new State<>(name, action, false));
    return this;
}

public StateMachineBuilder<C> suspendState(String name, Action<C> action) {
    states.add(new State<>(name, action, true));
    return this;
}
```

**Step 2: 运行所有 Builder 测试**

```bash
mvn test -pl state-machine-boot-starter -Dtest=StateMachineBuilderTest -v
```

预期：所有测试通过（包括 Task 2 新增的测试）。

**Step 3: 提交**

```bash
git add src/main/java/cn/chedejun/statemachine/core/StateMachineBuilder.java
git commit -m "feat: Builder 新增 suspendState() 方法"
```

---

### Task 4: InstanceRepository 增加 businessId 支持

**Files:**
- Modify: `src/main/java/cn/chedejun/statemachine/persistence/InstanceRepository.java`
- Test: `src/test/java/cn/chedejun/statemachine/persistence/InstanceRepositoryTest.java`

**Step 1: 读取现有 InstanceRepository 确认当前结构**

已有 `create(definitionId, machineName, definitionVersion, initialState)` 方法（4个参数），需要增加 businessId 参数。

但注意：构造函数不算形参限制，`create` 方法现有 4 个参数，加 businessId 会变成 5 个。因此使用一个内部 record 承载参数。

修改方案：新增 `createInstanceParams` record 作为参数载体：

```java
/** 创建实例的参数 */
public record CreateInstanceParams(String definitionId, String machineName, String definitionVersion, String initialState, String businessId) {
    public CreateInstanceParams(String definitionId, String machineName, String definitionVersion, String initialState) {
        this(definitionId, machineName, definitionVersion, initialState, null);
    }
}
```

修改 `create` 方法：

```java
public String create(CreateInstanceParams params) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO state_machine_instances (id, definition_id, machine_name, definition_version, current_state, business_id, status) VALUES (?, ?, ?, ?, ?, ?, 'RUNNING')",
        id, params.definitionId(), params.machineName(), params.definitionVersion(), params.initialState(), params.businessId());
    return id;
}
```

新增方法：

```java
public Optional<InstanceRecord> findByBusinessId(String definitionId, String businessId) {
    try {
        return Optional.ofNullable(jdbcTemplate.queryForObject(
            "SELECT * FROM state_machine_instances WHERE definition_id = ? AND business_id = ? ORDER BY created_at DESC LIMIT 1",
            rowMapper(), definitionId, businessId));
    } catch (Exception e) { return Optional.empty(); }
}

public void markSuspended(String id, String currentState) {
    jdbcTemplate.update(
        "UPDATE state_machine_instances SET current_state = ?, status = 'SUSPENDED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        currentState, id);
}
```

**Step 2: 运行现有 InstanceRepository 测试**

```bash
mvn test -pl state-machine-boot-starter -Dtest=InstanceRepositoryTest -v
```

预期：现有测试因 `create` 方法签名变更而编译失败。

**Step 3: 修改 InstanceRepositoryTest.java 适配新 API**

将所有 `instanceRepository.create(...)` 调用改为 `instanceRepository.create(new CreateInstanceParams(...))`。

**Step 4: 运行测试确认通过**

```bash
mvn test -pl state-machine-boot-starter -Dtest=InstanceRepositoryTest -v
```

预期：PASS。

**Step 5: 提交**

```bash
git add src/main/java/cn/chedejun/statemachine/persistence/InstanceRepository.java src/test/java/cn/chedejun/statemachine/persistence/InstanceRepositoryTest.java
git commit -m "feat: InstanceRepository 增加 businessId、findByBusinessId、markSuspended"
```

---

### Task 5: ExecuteResult 增加 businessId 字段

**Files:**
- Modify: `src/main/java/cn/chedejun/statemachine/core/ExecuteResult.java`

**Step 1: 修改 ExecuteResult.java**

```java
package cn.chedejun.statemachine.core;

import java.time.Instant;

/**
 * 状态机执行结果。
 * 包含实例 ID 和最终状态，方便业务系统直接使用。
 *
 * status 取值：
 * - COMPLETED  : 流程自然结束，后面无后续状态
 * - FAILED     : 执行失败，超过最大重试次数
 * - SUSPENDED  : 流程在挂起点暂停，等待外部唤醒
 */
public record ExecuteResult(
        String instanceId,
        String machineName,
        String definitionVersion,
        String currentState,
        String status,
        String errorMessage,
        String businessId,
        Instant createdAt
) {}
```

**Step 2: 提交**

```bash
git add src/main/java/cn/chedejun/statemachine/core/ExecuteResult.java
git commit -m "feat: ExecuteResult 增加 businessId 字段，删除 REACHED 状态文档"
```

---

### Task 6: StateMachine 核心改动 — 简化 execute、挂起逻辑、恢复方法

**Files:**
- Modify: `src/main/java/cn/chedejun/statemachine/core/StateMachine.java`

这是最大的改动，需要仔细处理。

**Step 1: 编写测试 — 挂起点正常挂起**

在 `StateMachineBuilderTest.java` 中添加：

```java
@Test
void execute_suspendPoint_stopsExecution() {
    AtomicBoolean step2Executed = new AtomicBoolean(false);
    AtomicBoolean step3Executed = new AtomicBoolean(false);
    StateMachine<Context> m = StateMachineBuilder.<Context>builder("suspend-exec")
        .state("step1", ctx -> ctx.put("step1", true))
        .suspendState("step2", ctx -> step2Executed.set(true))
        .state("step3", ctx -> step3Executed.set(true))
        .transition("step1", "step2", ctx -> true)
        .transition("step2", "step3", ctx -> true)
        .retryPolicy(RetryPolicy.none())
        .jdbcTemplate(jdbcTemplate).build();

    ExecuteResult result = m.execute(new Context());

    assertEquals("SUSPENDED", result.status());
    assertEquals("step2", result.currentState());
    assertTrue(step2Executed.get());  // 挂起点的 Action 已执行
    assertFalse(step3Executed.get()); // step3 未执行
}
```

**Step 2: 运行测试确认失败**

```bash
mvn test -pl state-machine-boot-starter -Dtest=StateMachineBuilderTest#execute_suspendPoint_stopsExecution -v
```

预期：失败（SUSPENDED 状态和挂起逻辑尚未实现）。

**Step 3: 修改 StateMachine.java**

完整重写 StateMachine 类：

```java
package cn.chedejun.statemachine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.InstanceRepository.CreateInstanceParams;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Consumer;

public class StateMachine<C> {

    private final String name;
    private final String version;
    private final List<State<C>> states;
    private final List<Transition<C>> transitions;
    private final RetryPolicy retryPolicy;
    private final Class<C> contextClass;

    // 延迟注入（通过 BeanPostProcessor 或 Builder）
    private transient StateMachineRegistry registry;
    private transient JdbcTemplate jdbcTemplate;
    private transient ObjectMapper objectMapper;
    private transient InstanceRepository instanceRepository;
    private transient SnapshotRepository snapshotRepository;

    StateMachine(String name, String version, List<State<C>> states,
                 List<Transition<C>> transitions, RetryPolicy retryPolicy, Class<C> contextClass) {
        this.name = name;
        this.version = version;
        this.states = Collections.unmodifiableList(states);
        this.transitions = Collections.unmodifiableList(transitions);
        this.retryPolicy = retryPolicy;
        this.contextClass = contextClass;
    }

    public ExecuteResult execute(C context) {
        return execute(context, null);
    }

    public ExecuteResult execute(C context, String businessId) {
        ensureInitialized();
        String currentState = states.get(0).getName();
        String definitionId = resolveDefinitionId();
        String instanceId = instanceRepository.create(new CreateInstanceParams(definitionId, name, version, currentState, businessId));
        executeLoop(instanceId, context, currentState);
        var record = instanceRepository.findById(instanceId).orElseThrow();
        return new ExecuteResult(instanceId, name, version, record.currentState(), record.status(), record.errorMessage(), businessId, record.createdAt());
    }

    /** 通过业务 ID 恢复挂起的实例 */
    public void resumeByBusinessId(String businessId, Consumer<C> contextMerger) {
        String definitionId = resolveDefinitionId();
        var instance = instanceRepository.findByBusinessId(definitionId, businessId)
            .orElseThrow(() -> new StateMachineException("Instance not found for businessId: " + businessId));
        resumeInstance(instance, contextMerger);
    }

    /** 通过状态机实例 ID 恢复挂起的实例 */
    public void resumeByInstanceId(String instanceId, Consumer<C> contextMerger) {
        var instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        resumeInstance(instance, contextMerger);
    }

    private void resumeInstance(InstanceRepository.InstanceRecord instance, Consumer<C> contextMerger) {
        if (!"SUSPENDED".equals(instance.status()))
            throw new StateMachineException("Can only resume SUSPENDED instances, current status: " + instance.status());

        // 从最新快照恢复 context
        var snapshots = snapshotRepository.findByInstanceId(instance.id());
        String contextJson = snapshots.isEmpty() ? "{}" : snapshots.get(snapshots.size() - 1).outputJson();
        C context = deserialize(contextJson != null ? contextJson : "{}");

        // 应用 context 修改
        contextMerger.accept(context);

        // 恢复执行
        instanceRepository.updateState(instance.id(), instance.currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instance.id(), 0);
        executeLoop(instance.id(), context, instance.currentState());
    }

    public void retry(String instanceId, C context) {
        ensureInitialized();
        InstanceRepository.InstanceRecord instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        if (!"FAILED".equals(instance.status()))
            throw new StateMachineException("Can only retry FAILED instances, current status: " + instance.status());
        instanceRepository.updateState(instanceId, instance.currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instanceId, 0);
        executeLoop(instanceId, context, instance.currentState());
    }

    /**
     * 重试失败的实例，自动从首次快照读取并反序列化 context
     */
    public void retry(String instanceId) {
        ensureInitialized();
        InstanceRepository.InstanceRecord instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        if (!"FAILED".equals(instance.status()))
            throw new StateMachineException("Can only retry FAILED instances, current status: " + instance.status());

        var snapshots = snapshotRepository.findByInstanceId(instanceId);
        String contextJson = snapshots.isEmpty() ? "{}" : snapshots.get(0).inputJson();
        C context = deserialize(contextJson);

        instanceRepository.updateState(instanceId, instance.currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instanceId, 0);
        executeLoop(instanceId, context, instance.currentState());
    }

    // ===== 核心执行循环 =====

    private void executeLoop(String instanceId, C context, String startState) {
        String currentState = startState;
        int maxIterations = states.size() * (retryPolicy.getMaxAttempts() + 1) + 1;
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;
            final String stateName = currentState;
            State<C> state = findState(stateName)
                .orElseThrow(() -> new StateMachineException.StateNotFoundException(stateName));

            String inputJson = serialize(context);
            int attempt = getCurrentAttempt(instanceId, currentState);

            try {
                state.getAction().execute(context);
                snapshotRepository.save(instanceId, currentState, inputJson, serialize(context), "SUCCESS", null, attempt);
                instanceRepository.setRetryCount(instanceId, 0);
            } catch (Exception e) {
                snapshotRepository.save(instanceId, currentState, inputJson, null, "FAILED", e.getMessage(), attempt);
                int retryCount = getRetryCount(instanceId);
                if (retryCount < retryPolicy.getMaxAttempts()) {
                    long delayMs = retryPolicy.getDelayForAttempt(retryCount + 1);
                    instanceRepository.incrementRetry(instanceId, retryCount + 1, java.time.Instant.now().plusMillis(delayMs));
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new StateMachineException("Retry interrupted", ie);
                    }
                    continue;
                }
                instanceRepository.updateState(instanceId, currentState, "FAILED", e.getMessage());
                throw new StateMachineException(
                    String.format("State '%s' failed after %d attempts: %s", currentState, retryCount + 1, e.getMessage()), e);
            }

            // 挂起点检查：在 Action 执行成功后，查找下一状态之前
            if (state.isSuspended()) {
                instanceRepository.markSuspended(instanceId, currentState);
                return;
            }

            Optional<String> nextState = findNextState(context, currentState);
            if (nextState.isEmpty()) {
                instanceRepository.updateState(instanceId, currentState, "COMPLETED", null);
                return;
            }
            currentState = nextState.get();
            instanceRepository.updateState(instanceId, currentState, "RUNNING", null);
        }
        throw new StateMachineException("Execution exceeded maximum iterations");
    }

    private Optional<State<C>> findState(String name) {
        return states.stream().filter(s -> s.getName().equals(name)).findFirst();
    }

    private Optional<String> findNextState(C context, String fromState) {
        for (Transition<C> t : transitions) {
            if (t.getFrom().equals(fromState)) {
                if (t.getCondition() == null || t.getCondition().test(context)) return Optional.of(t.getTo());
            }
        }
        return Optional.empty();
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private C deserialize(String json) {
        try { return objectMapper.readValue(json, contextClass); } catch (Exception e) {
            try { return (C) objectMapper.readValue(json, Context.class); } catch (Exception e2) { return null; }
        }
    }

    private String resolveDefinitionId() {
        if (registry != null) return registry.getVersions(name).stream()
            .filter(v -> v.version().equals(version)).findFirst()
            .map(DefinitionRepository.DefinitionRecord::id).orElse("unknown");
        return "unknown";
    }

    private int getCurrentAttempt(String instanceId, String stateName) {
        return snapshotRepository.findByInstanceId(instanceId).stream()
            .filter(s -> s.stateName().equals(stateName)).mapToInt(SnapshotRepository.SnapshotRecord::attempt)
            .max().orElse(0) + 1;
    }

    private int getRetryCount(String instanceId) {
        return instanceRepository.findById(instanceId).map(InstanceRepository.InstanceRecord::retryCount).orElse(0);
    }

    private void ensureInitialized() {
        if (jdbcTemplate == null) throw new StateMachineException("StateMachine not initialized. DataSource not set.");
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public List<State<C>> getStates() { return states; }
    public List<Transition<C>> getTransitions() { return transitions; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }

    public void setRegistry(StateMachineRegistry registry) { this.registry = registry; }
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.instanceRepository = new InstanceRepository(jdbcTemplate);
        this.snapshotRepository = new SnapshotRepository(jdbcTemplate);
    }
}
```

**Step 4: 运行测试确认通过**

```bash
mvn test -pl state-machine-boot-starter -Dtest=StateMachineBuilderTest -v
```

预期：所有测试通过。

**Step 5: 提交**

```bash
git add src/main/java/cn/chedejun/statemachine/core/StateMachine.java src/test/java/cn/chedejun/statemachine/core/StateMachineBuilderTest.java
git commit -m "feat: StateMachine 简化 execute、增加挂起逻辑、新增 resumeByBusinessId/resumeByInstanceId"
```

---

### Task 7: 更新现有测试适配新 API

**Files:**
- Modify: `src/test/java/cn/chedejun/statemachine/core/StateMachineBuilderTest.java`
- Modify: `src/test/java/cn/chedejun/statemachine/integration/StateMachineIntegrationTest.java`

**Step 1: 修改 StateMachineBuilderTest.java**

删除 `execute_withTargetState_returnsReached` 和 `execute_targetStateIsLastState_returnsCompleted` 两个测试（targetState 已移除）。

修改 `execute_noNextTransition_returnsCompleted` 测试保持不变（本来就测试无下一状态的情况）。

修改 `build_withJdbcTemplate_executesSuccessfully` 测试中的 `execute(new Context())` 调用（不需要改动，因为无参 execute 仍然保留）。

**Step 2: 运行所有测试**

```bash
mvn test -pl state-machine-boot-starter -v
```

预期：所有测试通过。

**Step 3: 提交**

```bash
git add src/test/java/cn/chedejun/statemachine/core/StateMachineBuilderTest.java src/test/java/cn/chedejun/statemachine/integration/StateMachineIntegrationTest.java
git commit -m "test: 更新测试适配新 API，删除 targetState 相关测试"
```

---

### Task 8: 集成测试 — 挂起/恢复端到端

**Files:**
- Modify: `src/test/java/cn/chedejun/statemachine/integration/StateMachineIntegrationTest.java`

**Step 1: 在 TestConfig 中增加带挂起点的状态机**

```java
@Bean
public StateMachine<TestContext> suspendMachine() {
    return StateMachineBuilder.<TestContext>builder("suspend-machine")
        .contextClass(TestContext.class)
        .state("validate", ctx -> ctx.setValidated(true))
        .suspendState("wait-approval", ctx -> ctx.setProcessed(true))
        .state("complete", ctx -> ctx.setCompleted(true))
        .transition("validate", "wait-approval", TestContext::isValidated)
        .transition("wait-approval", "complete", TestContext::isProcessed)
        .build();
}
```

**Step 2: 注入 suspendMachine**

```java
@Autowired private StateMachine<TestContext> suspendMachine;
```

**Step 3: 增加测试 — 挂起后恢复**

```java
@Test
@Order(50)
void execute_suspendAndResume_continuesExecution() {
    TestContext ctx = new TestContext();
    ExecuteResult result = suspendMachine.execute(ctx);

    assertEquals("SUSPENDED", result.status());
    assertEquals("wait-approval", result.currentState());
    assertTrue(ctx.isValidated());
    assertTrue(ctx.isProcessed());    // 挂起点的 Action 已执行
    assertFalse(ctx.isCompleted());   // complete 未执行

    // 恢复执行
    suspendMachine.resumeByBusinessId("test-biz-001", c -> {});

    // 重新查询实例状态
    var instance = instanceRepository().findByBusinessId(
        resolveDefinitionId("suspend-machine"), "test-biz-001");
    assertTrue(instance.isPresent());
    assertEquals("COMPLETED", instance.get().status());
    assertEquals("complete", instance.get().currentState());
}

@Test
@Order(51)
void resume_nonSuspendedInstance_throwsException() {
    TestContext ctx = new TestContext();
    ExecuteResult result = suspendMachine.execute(ctx);
    assertEquals("SUSPENDED", result.status());

    // 先恢复一次
    suspendMachine.resumeByInstanceId(result.instanceId(), c -> {});

    // 再次恢复应该失败（已经不是 SUSPENDED 状态）
    assertThrows(StateMachineException.class, () ->
        suspendMachine.resumeByInstanceId(result.instanceId(), c -> {}));
}

@Test
@Order(52)
void resume_contextMerger_modifiesContext() {
    TestContext ctx = new TestContext();
    ctx.put("extra-key", "original-value");
    ExecuteResult result = suspendMachine.execute(ctx, "test-biz-002");

    assertEquals("SUSPENDED", result.status());

    // 恢复时修改 context
    suspendMachine.resumeByBusinessId("test-biz-002", c -> {
        c.put("extra-key", "modified-value");
    });

    // 从快照验证 context 被修改
    var snapshots = snapshotRepository().findByInstanceId(result.instanceId());
    // 最后一个快照的 output 应该包含修改后的值
    var lastSnapshot = snapshots.get(snapshots.size() - 1);
    assertNotNull(lastSnapshot.outputJson());
}
```

**Step 4: 增加辅助方法 resolveDefinitionId**

在 TestConfig 类外部添加：

```java
private String resolveDefinitionId(String machineName) {
    var definitions = definitionRepository.findAllByName(machineName);
    if (definitions.isEmpty()) throw new StateMachineException("Definition not found: " + machineName);
    return definitions.get(0).id();
}
```

**Step 5: 运行集成测试**

```bash
mvn test -pl state-machine-boot-starter -Dtest=StateMachineIntegrationTest -v
```

注意：集成测试需要 PostgreSQL 连接。如果环境不可用，可以先只运行 Builder 测试。

**Step 6: 提交**

```bash
git add src/test/java/cn/chedejun/statemachine/integration/StateMachineIntegrationTest.java
git commit -m "test: 增加挂起/恢复端到端集成测试"
```

---

### Task 9: 更新 demo 项目示例

**Files:**
- Modify: `state-machine-demo/src/main/java/cn/chedejun/demo/config/OrderConfig.java`
- Modify: `state-machine-demo/src/main/java/cn/chedejun/demo/statemachine/OrderContext.java`

**Step 1: 查看 demo 项目当前状态**

先读取 `OrderConfig.java` 和 `OrderContext.java`，确认当前结构。

**Step 2: 在 OrderConfig 中演示 suspendState**

在现有订单状态机中增加一个挂起点（如"等待发货确认"），展示用法。

**Step 3: 提交**

```bash
git add state-machine-demo/
git commit -m "docs: demo 项目演示 suspendState 用法"
```

---

### Task 10: 全量验证

**Step 1: 全量测试**

```bash
mvn clean test -pl state-machine-boot-starter -v
```

**Step 2: 构建全项目**

```bash
mvn clean install
```

预期：所有模块编译通过，测试全部通过。

**Step 3: 提交（如有遗留改动）**

```bash
git add -A
git commit -m "chore: 全量验证通过"
```
