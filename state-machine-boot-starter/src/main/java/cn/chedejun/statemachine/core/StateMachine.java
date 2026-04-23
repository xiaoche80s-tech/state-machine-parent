package cn.chedejun.statemachine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;

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

    public ExecuteResult execute(C context) { return execute(context, null, null); }
    public ExecuteResult execute(C context, String targetState) { return execute(context, null, targetState); }

    public ExecuteResult execute(C context, String startState, String targetState) {
        ensureInitialized();
        String currentState = startState != null ? startState : states.get(0).getName();
        String definitionId = resolveDefinitionId();
        String instanceId = instanceRepository.create(new cn.chedejun.statemachine.persistence.InstanceRepository.CreateInstanceParams(definitionId, name, version, currentState));
        executeLoop(instanceId, context, currentState, targetState);
        var record = instanceRepository.findById(instanceId).orElseThrow();
        return new ExecuteResult(instanceId, name, version, record.currentState(), record.status(), record.errorMessage(), record.businessId(), record.createdAt());
    }

    public void retry(String instanceId, C context) {
        ensureInitialized();
        InstanceRepository.InstanceRecord instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        if (!"FAILED".equals(instance.status()))
            throw new StateMachineException("Can only retry FAILED instances, current status: " + instance.status());
        instanceRepository.updateState(instanceId, instance.currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instanceId, 0);
        executeLoop(instanceId, context, instance.currentState(), null);
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

        // 从首次快照获取原始 context
        var snapshots = snapshotRepository.findByInstanceId(instanceId);
        String contextJson = snapshots.isEmpty() ? "{}" : snapshots.get(0).inputJson();
        C context = deserialize(contextJson);

        instanceRepository.updateState(instanceId, instance.currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instanceId, 0);
        executeLoop(instanceId, context, instance.currentState(), null);
    }

    // ===== 核心执行循环（execute 和 retry 共享） =====

    private void executeLoop(String instanceId, C context, String startState, String targetState) {
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
                    instanceRepository.incrementRetry(instanceId, retryCount + 1, Instant.now().plusMillis(delayMs));
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

            if (targetState != null && currentState.equals(targetState)) {
                if (findNextState(context, currentState).isEmpty()) {
                    instanceRepository.updateState(instanceId, currentState, "COMPLETED", null);
                } else {
                    instanceRepository.updateState(instanceId, currentState, "REACHED", null);
                }
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
