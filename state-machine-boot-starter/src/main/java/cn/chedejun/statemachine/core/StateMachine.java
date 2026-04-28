package cn.chedejun.statemachine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

public class StateMachine<C> {

    private static final Logger log = LoggerFactory.getLogger(StateMachine.class);

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

    public ExecuteResult execute(C context, String businessId) {
        ensureInitialized();
        String currentState = states.get(0).getName();
        String definitionId = resolveDefinitionId();
        String instanceId = instanceRepository.create(new InstanceRepository.CreateInstanceParams(definitionId, name, version, currentState, businessId));
        executeLoop(instanceId, context, currentState);
        var record = instanceRepository.findById(instanceId).orElseThrow();
        return new ExecuteResult(instanceId, name, version, record.currentState(), record.status(), record.errorMessage(), businessId, record.createdAt());
    }

    /** 通过业务 ID 恢复挂起的实例 */
    public void resumeByBusinessId(String stateMachineName, String businessId, String expectedCurrentState, Consumer<C> contextMerger) {
        var instance = instanceRepository.findByBusinessId(stateMachineName, businessId)
            .orElseThrow(() -> new StateMachineException("Instance not found for stateMachine=" + stateMachineName + ", businessId=" + businessId));
        resumeInstance(instance, expectedCurrentState, contextMerger);
    }

    /** 通过状态机实例 ID 恢复挂起的实例 */
    public void resumeByInstanceId(String instanceId, String expectedCurrentState, Consumer<C> contextMerger) {
        var instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        resumeInstance(instance, expectedCurrentState, contextMerger);
    }

    private void resumeInstance(InstanceRepository.InstanceRecord instance, String expectedCurrentState, Consumer<C> contextMerger) {
        if (!instance.currentState().equals(expectedCurrentState))
            throw new StateMachineException(String.format("State mismatch: expected '%s', actual '%s'", expectedCurrentState, instance.currentState()));

        // 从最新快照恢复 context
        var snapshots = snapshotRepository.findByInstanceId(instance.id());
        String contextJson = snapshots.isEmpty() ? "{}" : snapshots.get(snapshots.size() - 1).outputJson();
        C context = deserialize(contextJson != null ? contextJson : "{}");

        // 应用 context 修改
        contextMerger.accept(context);

        // 恢复执行：先从挂起点找到下一状态，再继续执行循环
        Optional<String> nextState = findNextState(context, instance.currentState());
        if (nextState.isPresent()) {
            // 所有准备工作完成后，原子地将 SUSPENDED 切换为 RUNNING
            int updated = instanceRepository.tryMarkRunningFromSuspended(instance.id());
            if (updated == 0)
                throw new StateMachineException("Instance already resumed or not suspended: " + instance.id());

            instanceRepository.updateState(instance.id(), nextState.get(), "RUNNING", null);
            instanceRepository.setRetryCount(instance.id(), 0);
            executeLoop(instance.id(), context, nextState.get());
        } else {
            // 恢复后发现无匹配过渡：区分终端状态和路由失败
            if (hasOutgoingTransitions(instance.currentState())) {
                String availableTargets = transitions.stream()
                    .filter(t -> t.getFrom().equals(instance.currentState()))
                    .map(Transition::getTo)
                    .collect(java.util.stream.Collectors.joining(", "));
                String errorMsg = String.format("No matching transition from state '%s'. Available transitions: %s", instance.currentState(), availableTargets);
                instanceRepository.updateState(instance.id(), instance.currentState(), "FAILED", errorMsg);
            } else {
                instanceRepository.updateState(instance.id(), instance.currentState(), "COMPLETED", null);
            }
        }
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
     * 重试失败的实例，自动从最后一个失败快照的 IN 参数读取并反序列化 context。
     * 如果最后一次失败是路由评估（ROUTE），则跳过 action，直接从目标状态继续执行。
     */
    public void retry(String instanceId) {
        ensureInitialized();
        InstanceRepository.InstanceRecord instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        if (!"FAILED".equals(instance.status()))
            throw new StateMachineException("Can only retry FAILED instances, current status: " + instance.status());

        var snapshots = snapshotRepository.findByInstanceId(instanceId);
        var lastFailed = snapshots.stream()
            .filter(s -> "FAILED".equals(s.status()))
            .max(Comparator.comparing(SnapshotRepository.SnapshotRecord::executedAt));

        if (lastFailed.isEmpty())
            throw new StateMachineException("No failed snapshot found for instance: " + instanceId);

        SnapshotRepository.SnapshotRecord failedSnapshot = lastFailed.get();
        C context = deserialize(failedSnapshot.inputJson() != null ? failedSnapshot.inputJson() : "{}");


        // 路由评估失败：从 currentState 重新尝试路由（不重执行 action）
        if ("ROUTE".equals(failedSnapshot.snapshotType())) {
            Optional<String> nextState = findNextState(context, instance.currentState());
            if (nextState.isEmpty())
                throw new StateMachineException("No matching transition found from state: " + instance.currentState());

            String targetState = nextState.get();
            findState(targetState)
                .orElseThrow(() -> new StateMachineException.StateNotFoundException(targetState));
            snapshotRepository.updateRouteStatus(failedSnapshot.id(),"SUCCESS",targetState,"");
            instanceRepository.updateState(instanceId, targetState, "RUNNING", null);
            instanceRepository.setRetryCount(instanceId, 0);
            executeLoop(instanceId, context, targetState);
            return;
        }

        // 节点执行失败：正常重试，重执行当前状态的 action
        instanceRepository.updateState(instanceId, instance.currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instanceId, 0);
        executeLoop(instanceId, context, instance.currentState());
        // 重试成功，删除旧的失败快照
    }

    // ===== 核心执行循环（execute 和 retry 共享） =====

    private void executeLoop(String instanceId, C context, String startState) {
        final String[] current = { startState };
        int maxIterations = states.size() * (retryPolicy.getMaxAttempts() + 1) + 1;
        int iteration = 0;

        try {
            while (iteration < maxIterations) {
                iteration++;
                final String stateName = current[0];
                State<C> state = findState(stateName)
                    .orElseThrow(() -> {
                        instanceRepository.updateState(instanceId, stateName, "FAILED", "State not found: " + stateName);
                        return new StateMachineException.StateNotFoundException(stateName);
                    });

                String inputJson = serialize(context);
                int attempt = getCurrentAttempt(instanceId, stateName);

                try {
                    state.getAction().execute(context);
                    snapshotRepository.save(instanceId, stateName, inputJson, serialize(context), "SUCCESS", null, attempt, "NODE");
                    instanceRepository.setRetryCount(instanceId, 0);
                } catch (Exception e) {
                    log.error("[state-machine] State '{}' action failed (instance {}, attempt {})", stateName, instanceId, attempt, e);
                    snapshotRepository.save(instanceId, stateName, inputJson, null, "FAILED", e.getMessage(), attempt, "NODE");
                    int retryCount = getRetryCount(instanceId);
                    if (retryCount < retryPolicy.getMaxAttempts()) {
                        long delayMs = retryPolicy.getDelayForAttempt(retryCount + 1);
                        instanceRepository.incrementRetry(instanceId, retryCount + 1, Instant.now().plusMillis(delayMs));
                        try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new StateMachineException("Retry interrupted", ie);
                        }
                        continue;
                    }
                    log.error("[state-machine] State '{}' exhausted {} retries (instance {})", stateName, retryCount + 1, instanceId);
                    instanceRepository.updateState(instanceId, stateName, "FAILED", e.getMessage());
                    throw new StateMachineException(
                        String.format("State '%s' failed after %d attempts: %s", stateName, retryCount + 1, e.getMessage()), e);
                }

                // 挂起点检查：在 Action 执行成功后，查找下一状态之前
                if (state.isSuspended()) {
                    instanceRepository.markSuspended(instanceId, stateName);
                    return;
                }

                try {
                    Optional<String> nextState = findNextState(context, stateName);
                    if (nextState.isEmpty()) {
                        // 区分"无出边（终端状态）"和"有出边但条件不匹配"
                        if (hasOutgoingTransitions(stateName)) {
                            // 有出边但无匹配条件 → 路由失败
                            String availableTargets = transitions.stream()
                                .filter(t -> t.getFrom().equals(stateName))
                                .map(Transition::getTo)
                                .collect(java.util.stream.Collectors.joining(", "));
                            String errorMsg = String.format("No matching transition from state '%s'. Available transitions: %s", stateName, availableTargets);
                            instanceRepository.updateState(instanceId, stateName, "FAILED", errorMsg);
                            throw new StateMachineException(errorMsg);
                        }
                        // 无出边 → 正常完成（终端状态）
                        instanceRepository.updateState(instanceId, stateName, "COMPLETED", null);
                        return;
                    }
                    snapshotRepository.saveRoute(instanceId, stateName, serialize(context), nextState.get());
                    current[0] = nextState.get();
                    instanceRepository.updateState(instanceId, current[0], "RUNNING", null);
                } catch (Exception e) {
                    log.error("[state-machine] Transition from '{}' failed (instance {})", stateName, instanceId, e);
                    snapshotRepository.saveRouteFailed(instanceId, stateName, serialize(context), e.getMessage());
                    instanceRepository.updateState(instanceId, stateName, "FAILED", e.getMessage());
                    throw new StateMachineException(
                        String.format("Transition from '%s' failed: %s", stateName, e.getMessage()), e);
                }
            }
            throw new StateMachineException("Execution exceeded maximum iterations");
        } catch (Exception e) {
            log.error("[state-machine] Execution loop error for instance {}", instanceId, e);
            String status = instanceRepository.findById(instanceId)
                .map(InstanceRepository.InstanceRecord::status).orElse(null);
            if (!"FAILED".equals(status)) {
                instanceRepository.updateState(instanceId, current[0], "FAILED", e.getMessage());
            }
            throw e;
        }
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

    private boolean hasOutgoingTransitions(String fromState) {
        for (Transition<C> t : transitions) {
            if (t.getFrom().equals(fromState)) return true;
        }
        return false;
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) {
            log.warn("[state-machine] Failed to serialize object, returning empty JSON", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private C deserialize(String json) {
        try { return (C) objectMapper.readValue(json, contextClass); } catch (Exception e) {
            log.error("[state-machine] Failed to deserialize context, contextClass={}", contextClass.getName(), e);
            throw new StateMachineException(
                String.format("Failed to deserialize context: %s. ContextClass: %s", e.getMessage(), contextClass.getName()), e);
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
        this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.ALWAYS);
        this.instanceRepository = new InstanceRepository(jdbcTemplate);
        this.snapshotRepository = new SnapshotRepository(jdbcTemplate);
    }
}
