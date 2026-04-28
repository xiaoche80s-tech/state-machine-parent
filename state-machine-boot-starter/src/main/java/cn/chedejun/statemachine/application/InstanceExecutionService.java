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
                String availableTargets = machine.getTransitions().stream()
                    .filter(t -> t.getFrom().equals(data.currentState().value()))
                    .map(Transition::getTo)
                    .collect(java.util.stream.Collectors.joining(", "));
                String errorMsg = String.format("No matching transition from state '%s'. Available: %s",
                    data.currentState().value(), availableTargets);
                instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.FAILED, errorMsg));
                throw new StateMachineException(errorMsg);
            } else {
                instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.COMPLETED, null));
            }
        }
    }

    public void resumeByInstanceId(StateMachine<C> machine, InstanceId instanceId,
                                    StateName expectedState, Consumer<C> contextMerger) {
        InstanceData data = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));

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
                String availableTargets = machine.getTransitions().stream()
                    .filter(t -> t.getFrom().equals(data.currentState().value()))
                    .map(Transition::getTo)
                    .collect(java.util.stream.Collectors.joining(", "));
                String errorMsg = String.format("No matching transition from state '%s'. Available: %s",
                    data.currentState().value(), availableTargets);
                instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.FAILED, errorMsg));
                throw new StateMachineException(errorMsg);
            } else {
                instanceRepo.save(data.withUpdatedState(data.currentState(), InstanceStatus.COMPLETED, null));
            }
        }
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

    private void executeLoop(InstanceId instanceId, C context, String startState, StateMachine<C> machine) {
        String[] current = { startState };
        int maxIterations = machine.getStates().size() * (machine.getRetryPolicy().getMaxAttempts() + 1) + 1;
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;
            String stateName = current[0];
            State<C> state = machine.findState(stateName)
                .orElseThrow(() -> {
                    InstanceData d = requireInstance(instanceId);
                    instanceRepo.save(d.withUpdatedState(StateName.of(stateName), InstanceStatus.FAILED, "State not found: " + stateName));
                    return new StateMachineException.StateNotFoundException(stateName);
                });

            String inputJson = serialize(context);
            int attempt = getCurrentAttempt(instanceId, stateName);

            try {
                state.getAction().execute(context);
                snapshotRepo.save(ExecutionSnapshot.createSuccess(
                    SnapshotId.generate(), instanceId, StateName.of(stateName),
                    inputJson, serialize(context), attempt));
                instanceRepo.save(requireInstance(instanceId)
                    .withIncrementedRetry(0, null));
            } catch (Exception e) {
                log.error("[state-machine] 状态 '{}' 执行失败 (实例 {}, 第 {} 次尝试)", stateName, instanceId, attempt, e);
                snapshotRepo.save(ExecutionSnapshot.createFailed(
                    SnapshotId.generate(), instanceId, StateName.of(stateName),
                    inputJson, e.getMessage(), attempt));

                int retryCount = instanceRepo.findById(instanceId).map(InstanceData::retryCount).orElse(0);
                if (retryCount < machine.getRetryPolicy().getMaxAttempts()) {
                    long delayMs = machine.getRetryPolicy().getDelayForAttempt(retryCount + 1);
                    InstanceData d = requireInstance(instanceId);
                    instanceRepo.save(d.withIncrementedRetry(retryCount + 1, Instant.now().plusMillis(delayMs)));
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new StateMachineException("Retry interrupted", ie);
                    }
                    continue;
                }

                log.error("[state-machine] 状态 '{}' 耗尽 {} 次重试 (实例 {})", stateName, retryCount + 1, instanceId);
                InstanceData d = requireInstance(instanceId);
                instanceRepo.save(d.withUpdatedState(StateName.of(stateName), InstanceStatus.FAILED, e.getMessage()));
                throw new StateMachineException(
                    String.format("State '%s' failed after %d attempts: %s", stateName, retryCount + 1, e.getMessage()), e);
            }

            if (state.isSuspended()) {
                InstanceData d = requireInstance(instanceId);
                instanceRepo.save(d.withUpdatedState(StateName.of(stateName), InstanceStatus.SUSPENDED, null));
                return;
            }

            try {
                Optional<String> nextState = machine.findNextState(context, stateName);
                if (nextState.isEmpty()) {
                    if (machine.hasOutgoingTransitions(stateName)) {
                        String errorMsg = String.format("No matching transition from state '%s'. Available: %s",
                            stateName, machine.getTransitions().stream()
                                .filter(t -> t.getFrom().equals(stateName))
                                .map(Transition::getTo)
                                .collect(java.util.stream.Collectors.joining(", ")));
                        snapshotRepo.save(ExecutionSnapshot.createRouteFailed(
                            SnapshotId.generate(), instanceId, StateName.of(stateName),
                            serialize(context), errorMsg));
                        InstanceData d = requireInstance(instanceId);
                        instanceRepo.save(d.withUpdatedState(StateName.of(stateName), InstanceStatus.FAILED, errorMsg));
                        throw new StateMachineException(errorMsg);
                    }
                    InstanceData d = requireInstance(instanceId);
                    instanceRepo.save(d.withUpdatedState(StateName.of(stateName), InstanceStatus.COMPLETED, null));
                    return;
                }
                snapshotRepo.save(ExecutionSnapshot.createRoute(
                    SnapshotId.generate(), instanceId, StateName.of(stateName),
                    serialize(context), StateName.of(nextState.get())));
                current[0] = nextState.get();
                InstanceData d = requireInstance(instanceId);
                instanceRepo.save(d.withUpdatedState(StateName.of(nextState.get()), InstanceStatus.RUNNING, null));
            } catch (StateMachineException e) {
                throw e;
            } catch (Exception e) {
                log.error("[state-machine] 状态转换从 '{}' 失败 (实例 {})", stateName, instanceId, e);
                snapshotRepo.save(ExecutionSnapshot.createRouteFailed(
                    SnapshotId.generate(), instanceId, StateName.of(stateName),
                    serialize(context), e.getMessage()));
                InstanceData d = requireInstance(instanceId);
                instanceRepo.save(d.withUpdatedState(StateName.of(stateName), InstanceStatus.FAILED, e.getMessage()));
                throw new StateMachineException(
                    String.format("Transition from '%s' failed: %s", stateName, e.getMessage()), e);
            }
        }
        throw new StateMachineException("Execution exceeded maximum iterations");
    }

    private int getCurrentAttempt(InstanceId instanceId, String stateName) {
        return snapshotRepo.findByInstanceId(instanceId).stream()
            .filter(s -> s.stateName().value().equals(stateName))
            .mapToInt(SnapshotData::attempt)
            .max().orElse(0) + 1;
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
