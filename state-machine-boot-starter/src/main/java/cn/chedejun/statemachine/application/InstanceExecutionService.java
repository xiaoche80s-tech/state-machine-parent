package cn.chedejun.statemachine.application;

import cn.chedejun.statemachine.core.*;
import cn.chedejun.statemachine.domain.data.DefinitionData;
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
    private static final String SNAPSHOT_TYPE_ROUTE = "ROUTE";
    private static final String SNAPSHOT_TYPE_ADVANCE = "ADVANCE";
    private static final String UNKNOWN_VERSION = "unknown";
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

        StateName initialState = StateName.of(machine.getInitialState().getName());
        InstanceData finalData = executeLoop(instanceId, context, initialState, machine);

        return new ExecuteResult(
            finalData.id().value(), machine.getName(), machine.getVersion(),
            finalData.currentState().value(), finalData.status().name(),
            finalData.errorMessage(), businessId.value(), finalData.createdAt());
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
        InstanceData data = requireInstance(instanceId);
        resume(data, machine, expectedState, contextMerger);
    }

    public void retryWithCustomContext(StateMachine<C> machine, InstanceId instanceId, C context) {
        InstanceData data = requireInstance(instanceId);
        resetToRunningAndExecute(data, context, data.currentState().value(), machine);
    }

    public void retry(StateMachine<C> machine, InstanceId instanceId) {
        InstanceData data = requireInstance(instanceId);
        if (data.status() != InstanceStatus.FAILED)
            throw new StateMachineException("Can only retry FAILED instances, current status: " + data.status());

        List<SnapshotData> snapshots = snapshotRepo.findByInstanceId(instanceId);
        Optional<SnapshotData> lastFailed = snapshots.stream()
            .filter(s -> s.status() == ExecutionStatus.FAILED)
            .max(java.util.Comparator.comparing(SnapshotData::executedAt));

        if (!lastFailed.isPresent())
            throw new StateMachineException("No failed snapshot found for instance: " + instanceId);

        SnapshotData failedSnapshot = lastFailed.get();
        C context = deserialize(failedSnapshot.inputJson(), machine);

        if (SNAPSHOT_TYPE_ROUTE.equals(failedSnapshot.snapshotType())) {
            Optional<String> nextState = machine.findNextState(context, data.currentState().value());
            if (!nextState.isPresent())
                throw new StateMachineException("No matching transition found from state: " + data.currentState().value());

            String targetState = nextState.get();
            machine.findState(targetState)
                .orElseThrow(() -> new StateMachineException.StateNotFoundException(targetState));

            snapshotRepo.save(ExecutionSnapshot.createRoute(
                SnapshotId.generate(), instanceId,
                StateName.of(data.currentState().value()),
                serialize(context), StateName.of(targetState)));

            resetToRunningAndExecute(data, context, targetState, machine);
            return;
        }

        resetToRunningAndExecute(data, context, data.currentState().value(), machine);
    }

    /**
     * 推进失败的实例：用新的 Action 替代原失败的 Action 执行
     * 适用于 action 执行失败（非原子操作）后，业务数据已手动修复，需要用新 action 替代并继续流转的场景
     *
     * @param machine 状态机定义
     * @param instanceId 实例 ID
     * @param newAction 替代失败 action 的新 action
     * @param contextMerger 用于调整恢复后上下文的合并函数
     */
    public void advance(StateMachine<C> machine, InstanceId instanceId,
                        Action<C> newAction, Consumer<C> contextMerger) {
        // 1. 校验实例状态
        InstanceData data = requireInstance(instanceId);
        if (data.status() != InstanceStatus.FAILED)
            throw new StateMachineException("Can only advance FAILED instances, current status: " + data.status());

        // 2. 从快照恢复上下文：取最后一个 SUCCESS 快照的 outputJson（排除 ROUTE 和 ADVANCE）
        List<SnapshotData> snapshots = snapshotRepo.findByInstanceId(instanceId);
        String contextJson = snapshots.stream()
            .filter(s -> s.status() == ExecutionStatus.SUCCESS
                && !SNAPSHOT_TYPE_ROUTE.equals(s.snapshotType())
                && !SNAPSHOT_TYPE_ADVANCE.equals(s.snapshotType()))
            .reduce((first, second) -> second)
            .map(SnapshotData::outputJson)
            .orElse("{}");
        C context = deserialize(contextJson, machine);
        contextMerger.accept(context);

        // 3. CAS 重置实例状态为 RUNNING
        int updated = instanceRepo.tryMarkRunningFromFailed(instanceId);
        if (updated == 0)
            throw new StateMachineException("Instance already advanced or not in FAILED status: " + instanceId);

        // 4. 执行新 Action 并继续流转
        data = requireInstance(instanceId);
        String currentStateName = data.currentState().value();
        State<C> currentState = machine.findState(currentStateName)
            .orElseThrow(() -> new StateMachineException.StateNotFoundException(currentStateName));

        // 执行 advance action（带重试）
        data = executeAdvanceAction(data, currentState, newAction, context, machine);

        // 5. 路由到下一状态并继续 executeLoop
        Optional<String> nextState = machine.findNextState(context, currentStateName);
        if (!nextState.isPresent()) {
            if (machine.hasOutgoingTransitions(currentStateName)) {
                failWithTransitionError(instanceId, currentStateName, context, machine);
            }
            instanceRepo.save(data.withUpdatedState(
                StateName.of(currentStateName), InstanceStatus.COMPLETED, null));
            return;
        }

        // 记录 ROUTE 快照
        snapshotRepo.save(ExecutionSnapshot.createRoute(
            SnapshotId.generate(), instanceId, StateName.of(currentStateName),
            serialize(context), StateName.of(nextState.get())));

        // 继续执行后续状态
        instanceRepo.save(data.withUpdatedState(
            StateName.of(nextState.get()), InstanceStatus.RUNNING, null));
        executeLoop(instanceId, context, StateName.of(nextState.get()), machine);
    }

    /**
     * 公共恢复逻辑：恢复实例状态并继续执行
     */
    private void resume(InstanceData data, StateMachine<C> machine,
                        StateName expectedState, Consumer<C> contextMerger) {
        if (!data.currentState().value().equals(expectedState.value()))
            throw new StateMachineException(
                String.format("State mismatch: expected '%s', actual '%s'", expectedState.value(), data.currentState().value()));

        List<SnapshotData> snapshots = snapshotRepo.findByInstanceId(data.id());
        // 排除当前挂起点自身的快照，取进入该挂起点前上一个状态的最后一个 SUCCESS output（防累积）
        String contextJson = snapshots.stream()
            .filter(s -> s.status() == ExecutionStatus.SUCCESS
                && !SNAPSHOT_TYPE_ROUTE.equals(s.snapshotType())
                && !s.stateName().value().equals(data.currentState().value()))
            .reduce((first, second) -> second)
            .map(SnapshotData::outputJson)
            .orElse("{}");
        C context = deserialize(contextJson, machine);
        contextMerger.accept(context);

        // CAS：标记为 RUNNING，保持当前 suspend 状态
        int updated = instanceRepo.tryMarkRunningFromSuspended(data.id(), data.currentState());
        if (updated == 0)
            throw new StateMachineException("Instance already resumed or not suspended: " + data.id());

        // 执行 suspend 节点的 action + 检查条件 + 路由
        State<C> suspendState = machine.findState(data.currentState().value())
            .orElseThrow(() -> new StateMachineException.StateNotFoundException(data.currentState().value()));
        executeSuspendStateAction(data.id(), suspendState, context, machine);
    }

    /**
     * 核心执行循环：查找状态 → 执行 Action → 路由到下一个状态
     * @return 最终的 InstanceData
     */
    private InstanceData executeLoop(InstanceId instanceId, C context, StateName startState, StateMachine<C> machine) {
        InstanceData current = requireInstance(instanceId);
        String currentStateName = startState.value();
        int maxIterations = machine.getStates().size() * (machine.getRetryPolicy().getMaxAttempts() + 1) + 1;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            final InstanceData currentRef = current;
            final String stateNameRef = currentStateName;
            State<C> state = machine.findState(currentStateName)
                .orElseThrow(() -> {
                    instanceRepo.save(currentRef.withUpdatedState(StateName.of(stateNameRef), InstanceStatus.FAILED, "State not found: " + stateNameRef));
                    return new StateMachineException.StateNotFoundException(stateNameRef);
                });

            if (state.isSuspended()) {
                return instanceRepo.save(current.withUpdatedState(StateName.of(currentStateName), InstanceStatus.SUSPENDED, null));
            }

            current = executeAction(current, state, context, machine);

            Optional<String> nextState = machine.findNextState(context, currentStateName);
            if (!nextState.isPresent()) {
                if (machine.hasOutgoingTransitions(currentStateName)) {
                    failWithTransitionError(instanceId, currentStateName, context, machine);
                }
                return instanceRepo.save(current.withUpdatedState(StateName.of(currentStateName), InstanceStatus.COMPLETED, null));
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
     * attempt 直接由 current.retryCount()+1 推导，避免每次重试都查询全量快照（N+1 问题）
     * @return 更新后的 InstanceData
     */
    private InstanceData executeAction(InstanceData current, State<C> state, C context,
                                        StateMachine<C> machine) {
        String stateName = state.getName();
        int maxAttempts = machine.getRetryPolicy().getMaxAttempts();

        while (true) {
            int attempt = current.retryCount() + 1;
            String inputJson = serialize(context);
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

    /**
     * 执行 advance 操作的 Action，记录 ADVANCE 类型的快照
     * 与 executeAction 逻辑一致，但使用调用方传入的 action，快照类型为 "ADVANCE"
     */
    private InstanceData executeAdvanceAction(InstanceData current, State<C> state,
                                               Action<C> action, C context,
                                               StateMachine<C> machine) {
        String stateName = state.getName();
        int maxAttempts = machine.getRetryPolicy().getMaxAttempts();

        while (true) {
            int attempt = current.retryCount() + 1;
            String inputJson = serialize(context);
            try {
                action.execute(context);
                snapshotRepo.save(ExecutionSnapshot.createAdvanceSuccess(
                    SnapshotId.generate(), current.id(), StateName.of(stateName),
                    inputJson, serialize(context), attempt));
                return instanceRepo.save(current.withIncrementedRetry(0, null));
            } catch (Exception e) {
                log.error("[state-machine] ADVANCE 状态 '{}' 执行失败 (实例 {}, 第 {} 次尝试)", stateName, current.id(), attempt, e);
                snapshotRepo.save(ExecutionSnapshot.createAdvanceFailed(
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

                log.error("[state-machine] ADVANCE 状态 '{}' 耗尽 {} 次重试 (实例 {})", stateName, retryCount + 1, current.id());
                current = instanceRepo.save(current.withUpdatedState(StateName.of(stateName), InstanceStatus.FAILED, e.getMessage()));
                throw new StateMachineException(
                    String.format("ADVANCE state '%s' failed after %d attempts: %s", stateName, retryCount + 1, e.getMessage()), e);
            }
        }
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
        String safe = json != null && !json.trim().isEmpty() ? json : "{}";
        try { return (C) objectMapper.readValue(safe, machine.getContextClass()); } catch (Exception e) {
            throw new StateMachineException(String.format("Failed to deserialize context: %s", e.getMessage()), e);
        }
    }

    /**
     * 执行 suspend 节点的 action 并根据 resumeCondition 决定流转或重新挂起
     */
    private void executeSuspendStateAction(InstanceId instanceId, State<C> state, C context, StateMachine<C> machine) {
        InstanceData current = requireInstance(instanceId);
        current = executeAction(current, state, context, machine);

        // 检查 resumeCondition：条件不满足则回到 SUSPENDED
        if (state.hasResumeCondition() && !state.getResumeCondition().test(context)) {
            instanceRepo.save(current.withUpdatedState(StateName.of(state.getName()), InstanceStatus.SUSPENDED, null));
            return;
        }

        // 条件满足（或无 resumeCondition）：路由到下一状态
        Optional<String> nextState = machine.findNextState(context, state.getName());
        if (nextState.isPresent()) {
            String nextStateName = nextState.get();
            snapshotRepo.save(ExecutionSnapshot.createRoute(
                SnapshotId.generate(), instanceId, StateName.of(state.getName()),
                serialize(context), StateName.of(nextStateName)));
            instanceRepo.save(current.withUpdatedState(StateName.of(nextStateName), InstanceStatus.RUNNING, null));
            executeLoop(instanceId, context, StateName.of(nextStateName), machine);
        } else {
            if (machine.hasOutgoingTransitions(state.getName())) {
                failWithTransitionError(instanceId, state.getName(), context, machine);
            } else {
                instanceRepo.save(current.withUpdatedState(StateName.of(state.getName()), InstanceStatus.COMPLETED, null));
            }
        }
    }

    private void resetToRunningAndExecute(InstanceData data, C context, String startState, StateMachine<C> machine) {
        instanceRepo.save(data.withUpdatedState(StateName.of(startState), InstanceStatus.RUNNING, null)
            .withIncrementedRetry(0, null));

        // 如果目标状态是 suspend 节点，使用 suspend 专用逻辑（执行 action + 检查条件）
        Optional<State<C>> stateOpt = machine.findState(startState);
        if (stateOpt.isPresent() && stateOpt.get().isSuspended()) {
            executeSuspendStateAction(data.id(), stateOpt.get(), context, machine);
        } else {
            executeLoop(data.id(), context, StateName.of(startState), machine);
        }
    }

    private void failWithTransitionError(InstanceId instanceId, String currentStateName, C context, StateMachine<C> machine) {
        String errorMsg = buildTransitionError(currentStateName, machine.getTransitions());
        snapshotRepo.save(ExecutionSnapshot.createRouteFailed(
            SnapshotId.generate(), instanceId, StateName.of(currentStateName),
            serialize(context), errorMsg));
        instanceRepo.save(requireInstance(instanceId).withUpdatedState(StateName.of(currentStateName), InstanceStatus.FAILED, errorMsg));
        throw new StateMachineException(errorMsg);
    }

    private InstanceData requireInstance(InstanceId instanceId) {
        return instanceRepo.findById(instanceId)
            .orElseThrow(() -> new StateMachineException("Instance not found: " + instanceId));
    }

    private DefinitionId resolveDefinitionId(StateMachine<C> machine) {
        List<DefinitionData> definitions = definitionRepo.findAllByName(MachineName.of(machine.getName()));
        if (definitions.isEmpty()) return DefinitionId.of(UNKNOWN_VERSION);
        return DefinitionId.of(definitions.get(0).id());
    }

    private InstanceData toData(StateMachineInstance instance) {
        return new InstanceData(
            instance.id(), instance.definitionId(), instance.machineName(),
            UNKNOWN_VERSION, instance.currentState(), instance.businessId(),
            instance.status(), instance.retryCount(), null, instance.errorMessage(),
            Instant.now(), Instant.now());
    }
}
