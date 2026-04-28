package cn.chedejun.statemachine.application;

import cn.chedejun.statemachine.core.*;
import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.instance.StateMachineInstance;
import cn.chedejun.statemachine.domain.repository.DefinitionRepository;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.domain.shared.*;
import cn.chedejun.statemachine.domain.snapshot.ExecutionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class InstanceExecutionService<C> {

    private static final Logger log = LoggerFactory.getLogger(InstanceExecutionService.class);
    private final InstanceRepository instanceRepo;
    private final SnapshotRepository snapshotRepo;
    private final DefinitionRepository definitionRepo;
    private final ObjectMapper objectMapper;

    public InstanceExecutionService(InstanceRepository instanceRepo, SnapshotRepository snapshotRepo,
                                     DefinitionRepository definitionRepo, ObjectMapper objectMapper) {
        this.instanceRepo = instanceRepo;
        this.snapshotRepo = snapshotRepo;
        this.definitionRepo = definitionRepo;
        this.objectMapper = objectMapper;
    }

    public ExecuteResult execute(StateMachine<C> machine, C context, BusinessId businessId) {
        InstanceId instanceId = InstanceId.generate();
        DefinitionId definitionId = resolveDefinitionId(machine);

        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, businessId);
        instance.setDefinitionId(definitionId);
        instanceRepo.save(toData(instance));

        String initialState = machine.getStates().get(0).getName();
        executeLoop(instanceId, context, initialState, machine);

        InstanceData saved = requireInstance(instanceId);
        return new ExecuteResult(
            saved.id().value(), machine.getName(), machine.getVersion(),
            saved.currentState().value(), saved.status().name(),
            saved.errorMessage(), businessId.value(), saved.createdAt());
    }

    public void resumeByBusinessId(StateMachine<C> machine, BusinessId businessId,
                                    StateName expectedState, Consumer<C> contextMerger) {
        InstanceData data = instanceRepo.findByBusinessId(MachineName.of(machine.getName()), businessId)
            .orElseThrow(() -> new StateMachineException(
                "Instance not found for stateMachine=" + machine.getName() + ", businessId=" + businessId.value()));
        resume(data, machine, expectedState, contextMerger);
    }

    public void resumeByInstanceId(StateMachine<C> machine, InstanceId instanceId,
                                    StateName expectedState, Consumer<C> contextMerger) {
        InstanceData data = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        resume(data, machine, expectedState, contextMerger);
    }

    public void retryWithCustomContext(StateMachine<C> machine, InstanceId instanceId, C context) {
        InstanceData data = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        if (data.status() != InstanceStatus.FAILED)
            throw new StateMachineException("Can only retry FAILED instances, current status: " + data.status());

        instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.RUNNING, null)
            .withIncrementedRetry(0, null));
        executeLoop(instanceId, context, data.currentState().value(), machine);
    }

    public void retry(StateMachine<C> machine, InstanceId instanceId) {
        InstanceData data = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
        if (data.status() != InstanceStatus.FAILED)
            throw new StateMachineException("Can only retry FAILED instances, current status: " + data.status());

        var snapshots = snapshotRepo.findByInstanceId(instanceId);
        var lastFailed = snapshots.stream()
            .filter(s -> s.status() == ExecutionStatus.FAILED)
            .max(java.util.Comparator.comparing(SnapshotData::executedAt));

        if (lastFailed.isEmpty())
            throw new StateMachineException("No failed snapshot found for instance: " + instanceId);

        SnapshotData failedSnapshot = lastFailed.get();
        C context = deserialize(failedSnapshot.inputJson() != null ? failedSnapshot.inputJson() : "{}", machine);

        if ("ROUTE".equals(failedSnapshot.snapshotType())) {
            Optional<String> nextState = machine.findNextState(context, data.currentState().value());
            if (nextState.isEmpty())
                throw new StateMachineException("No matching transition found from state: " + data.currentState().value());

            String targetState = nextState.get();
            machine.findState(targetState)
                .orElseThrow(() -> new StateMachineException.StateNotFoundException(targetState));

            snapshotRepo.save(ExecutionSnapshot.createRoute(
                SnapshotId.generate(), instanceId,
                StateName.of(data.currentState().value()),
                serialize(context), StateName.of(targetState)));

            instanceRepo.save(data.withUpdatedState(StateName.of(targetState), InstanceStatus.RUNNING, null)
                .withIncrementedRetry(0, null));
            executeLoop(instanceId, context, targetState, machine);
            return;
        }

        instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.RUNNING, null)
            .withIncrementedRetry(0, null));
        executeLoop(instanceId, context, data.currentState().value(), machine);
    }

    /**
     * 公共恢复逻辑：恢复实例状态并继续执行
     */
    private void resume(InstanceData data, StateMachine<C> machine,
                        StateName expectedState, Consumer<C> contextMerger) {
        if (!data.currentState().value().equals(expectedState.value()))
            throw new StateMachineException(
                String.format("State mismatch: expected '%s', actual '%s'", expectedState.value(), data.currentState().value()));

        var snapshots = snapshotRepo.findByInstanceId(data.id());
        String contextJson = snapshots.isEmpty() ? "{}" : snapshots.get(snapshots.size() - 1).outputJson();
        C context = deserialize(contextJson != null ? contextJson : "{}", machine);
        contextMerger.accept(context);

        Optional<String> nextState = machine.findNextState(context, data.currentState().value());
        if (nextState.isPresent()) {
            int updated = instanceRepo.tryMarkRunningFromSuspended(data.id());
            if (updated == 0)
                throw new StateMachineException("Instance already resumed or not suspended: " + data.id());
            instanceRepo.save(data.withUpdatedState(StateName.of(nextState.get()), InstanceStatus.RUNNING, null));
            executeLoop(data.id(), context, nextState.get(), machine);
        } else {
            if (machine.hasOutgoingTransitions(data.currentState().value())) {
                String errorMsg = buildTransitionError(data.currentState().value(), machine.getTransitions());
                instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.FAILED, errorMsg));
                throw new StateMachineException(errorMsg);
            } else {
                instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.COMPLETED, null));
            }
        }
    }

    /**
     * 核心执行循环：查找状态 → 执行 Action → 路由到下一个状态
     */
    private void executeLoop(InstanceId instanceId, C context, String startState, StateMachine<C> machine) {
        InstanceData current = requireInstance(instanceId);
        String currentStateName = startState;
        int maxIterations = machine.getStates().size() * (machine.getRetryPolicy().getMaxAttempts() + 1) + 1;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            final InstanceData currentRef = current;
            final String stateNameRef = currentStateName;
            State<C> state = machine.findState(currentStateName)
                .orElseThrow(() -> {
                    instanceRepo.save(currentRef.withUpdatedState(StateName.of(stateNameRef), InstanceStatus.FAILED, "State not found: " + stateNameRef));
                    return new StateMachineException.StateNotFoundException(stateNameRef);
                });

            current = executeAction(current, state, context, machine);

            if (state.isSuspended()) {
                current = instanceRepo.save(current.withUpdatedState(StateName.of(currentStateName), InstanceStatus.SUSPENDED, null));
                return;
            }

            Optional<String> nextState = machine.findNextState(context, currentStateName);
            if (nextState.isEmpty()) {
                if (machine.hasOutgoingTransitions(currentStateName)) {
                    String errorMsg = buildTransitionError(currentStateName, machine.getTransitions());
                    snapshotRepo.save(ExecutionSnapshot.createRouteFailed(
                        SnapshotId.generate(), instanceId, StateName.of(currentStateName),
                        serialize(context), errorMsg));
                    current = instanceRepo.save(current.withUpdatedState(StateName.of(currentStateName), InstanceStatus.FAILED, errorMsg));
                    throw new StateMachineException(errorMsg);
                }
                current = instanceRepo.save(current.withUpdatedState(StateName.of(currentStateName), InstanceStatus.COMPLETED, null));
                return;
            }

            snapshotRepo.save(ExecutionSnapshot.createRoute(
                SnapshotId.generate(), instanceId, StateName.of(currentStateName),
                serialize(context), StateName.of(nextState.get())));
            currentStateName = nextState.get();
            current = instanceRepo.save(current.withUpdatedState(StateName.of(currentStateName), InstanceStatus.RUNNING, null));
        }
        throw new StateMachineException("Execution exceeded maximum iterations");
    }

    /**
     * 执行单个状态的 Action，记录成功/失败快照，处理重试逻辑
     * @return 更新后的 InstanceData（成功）或 null（挂起后不应继续）
     */
    private InstanceData executeAction(InstanceData current, State<C> state, C context,
                                        StateMachine<C> machine) {
        String stateName = state.getName();
        String inputJson = serialize(context);
        int maxAttempts = machine.getRetryPolicy().getMaxAttempts();

        while (true) {
            int attempt = getCurrentAttempt(stateName, current.id());
            try {
                state.getAction().execute(context);
                snapshotRepo.save(ExecutionSnapshot.createSuccess(
                    SnapshotId.generate(), current.id(), StateName.of(stateName),
                    inputJson, serialize(context), attempt));
                return instanceRepo.save(current.withIncrementedRetry(0, null));
            } catch (Exception e) {
                log.error("[state-machine] 状态 '{}' 执行失败 (实例 {}, 第 {} 次尝试)", stateName, current.id(), attempt, e);
                snapshotRepo.save(ExecutionSnapshot.createFailed(
                    SnapshotId.generate(), current.id(), StateName.of(stateName),
                    inputJson, e.getMessage(), attempt));

                int retryCount = current.retryCount();
                if (retryCount < maxAttempts) {
                    long delayMs = machine.getRetryPolicy().getDelayForAttempt(retryCount + 1);
                    current = instanceRepo.save(current.withIncrementedRetry(retryCount + 1, Instant.now().plusMillis(delayMs)));
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new StateMachineException("Retry interrupted", ie);
                    }
                    continue;
                }

                log.error("[state-machine] 状态 '{}' 耗尽 {} 次重试 (实例 {})", stateName, retryCount + 1, current.id());
                current = instanceRepo.save(current.withUpdatedState(StateName.of(stateName), InstanceStatus.FAILED, e.getMessage()));
                throw new StateMachineException(
                    String.format("State '%s' failed after %d attempts: %s", stateName, retryCount + 1, e.getMessage()), e);
            }
        }
    }

    private int getCurrentAttempt(String stateName, InstanceId instanceId) {
        return snapshotRepo.findByInstanceId(instanceId).stream()
            .filter(s -> s.stateName().value().equals(stateName))
            .mapToInt(SnapshotData::attempt)
            .max().orElse(0) + 1;
    }

    private String buildTransitionError(String stateName, List<? extends Transition> transitions) {
        String availableTargets = transitions.stream()
            .filter(t -> t.getFrom().equals(stateName))
            .map(Transition::getTo)
            .collect(java.util.stream.Collectors.joining(", "));
        return String.format("No matching transition from state '%s'. Available: %s", stateName, availableTargets);
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) {
            log.warn("[state-machine] 序列化对象失败", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private C deserialize(String json, StateMachine<C> machine) {
        try { return (C) objectMapper.readValue(json, machine.getContextClass()); } catch (Exception e) {
            throw new StateMachineException(String.format("Failed to deserialize context: %s", e.getMessage()), e);
        }
    }

    private InstanceData requireInstance(InstanceId instanceId) {
        return instanceRepo.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
    }

    private DefinitionId resolveDefinitionId(StateMachine<C> machine) {
        var definitions = definitionRepo.findAllByName(MachineName.of(machine.getName()));
        if (definitions.isEmpty()) return DefinitionId.of("unknown");
        return DefinitionId.of(definitions.get(0).id());
    }

    private InstanceData toData(StateMachineInstance instance) {
        return new InstanceData(
            instance.id(), instance.definitionId(), instance.machineName(),
            "unknown", instance.currentState(), instance.businessId(),
            instance.status(), instance.retryCount(), null, instance.errorMessage(),
            java.time.Instant.now(), java.time.Instant.now());
    }
}
