package cn.chedejun.statemachine.domain.snapshot;

import cn.chedejun.statemachine.domain.shared.*;

import java.time.Instant;

/**
 * 不可变快照聚合根。创建后不修改。
 * 路由评估失败时创建新 FAILED 快照，而非修改旧快照。
 */
public class ExecutionSnapshot extends AggregateRoot<SnapshotId> {

    private final InstanceId instanceId;
    private final StateName stateName;
    private final String inputJson;
    private final String outputJson;
    private final ExecutionStatus status;
    private final String errorMessage;
    private final int attempt;
    private final String snapshotType; // "NODE" or "ROUTE"
    private final Instant executedAt;

    private ExecutionSnapshot(SnapshotId id, InstanceId instanceId, StateName stateName,
                               String inputJson, String outputJson, ExecutionStatus status,
                               String errorMessage, int attempt, String snapshotType) {
        super(id);
        this.instanceId = instanceId;
        this.stateName = stateName;
        this.inputJson = inputJson;
        this.outputJson = outputJson;
        this.status = status;
        this.errorMessage = errorMessage;
        this.attempt = attempt;
        this.snapshotType = snapshotType;
        this.executedAt = Instant.now();
    }

    public static ExecutionSnapshot createSuccess(SnapshotId id, InstanceId instanceId, StateName stateName,
                                                   String inputJson, String outputJson, int attempt) {
        return new ExecutionSnapshot(id, instanceId, stateName, inputJson, outputJson,
                                      ExecutionStatus.SUCCESS, null, attempt, "NODE");
    }

    public static ExecutionSnapshot createFailed(SnapshotId id, InstanceId instanceId, StateName stateName,
                                                  String inputJson, String errorMessage, int attempt) {
        return new ExecutionSnapshot(id, instanceId, stateName, inputJson, null,
                                      ExecutionStatus.FAILED, errorMessage, attempt, "NODE");
    }

    public static ExecutionSnapshot createRoute(SnapshotId id, InstanceId instanceId, StateName fromState,
                                                 String contextJson, StateName toState) {
        return new ExecutionSnapshot(id, instanceId, fromState, contextJson,
                                      "\"" + toState.value() + "\"",
                                      ExecutionStatus.SUCCESS, null, 0, "ROUTE");
    }

    public static ExecutionSnapshot createRouteFailed(SnapshotId id, InstanceId instanceId, StateName fromState,
                                                       String contextJson, String errorMessage) {
        return new ExecutionSnapshot(id, instanceId, fromState, contextJson, null,
                                      ExecutionStatus.FAILED, errorMessage, 0, "ROUTE");
    }

    public InstanceId instanceId() { return instanceId; }
    public StateName stateName() { return stateName; }
    public String inputJson() { return inputJson; }
    public String outputJson() { return outputJson; }
    public ExecutionStatus status() { return status; }
    public String errorMessage() { return errorMessage; }
    public int attempt() { return attempt; }
    public String snapshotType() { return snapshotType; }
    public Instant executedAt() { return executedAt; }
}
