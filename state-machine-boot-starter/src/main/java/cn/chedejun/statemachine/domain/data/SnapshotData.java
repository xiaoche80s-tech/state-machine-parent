package cn.chedejun.statemachine.domain.data;

import cn.chedejun.statemachine.domain.shared.*;

import java.time.Instant;
import java.util.Objects;

public final class SnapshotData {
    private final SnapshotId id;
    private final InstanceId instanceId;
    private final StateName stateName;
    private final String inputJson;
    private final String outputJson;
    private final ExecutionStatus status;
    private final String errorMessage;
    private final int attempt;
    private final String snapshotType;
    private final Instant executedAt;

    public SnapshotData(SnapshotId id, InstanceId instanceId, StateName stateName,
                        String inputJson, String outputJson, ExecutionStatus status,
                        String errorMessage, int attempt, String snapshotType, Instant executedAt) {
        this.id = id;
        this.instanceId = instanceId;
        this.stateName = stateName;
        this.inputJson = inputJson;
        this.outputJson = outputJson;
        this.status = status;
        this.errorMessage = errorMessage;
        this.attempt = attempt;
        this.snapshotType = snapshotType;
        this.executedAt = executedAt;
    }

    public SnapshotId id() { return id; }
    public InstanceId instanceId() { return instanceId; }
    public StateName stateName() { return stateName; }
    public String inputJson() { return inputJson; }
    public String outputJson() { return outputJson; }
    public ExecutionStatus status() { return status; }
    public String errorMessage() { return errorMessage; }
    public int attempt() { return attempt; }
    public String snapshotType() { return snapshotType; }
    public Instant executedAt() { return executedAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnapshotData that = (SnapshotData) o;
        return attempt == that.attempt && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id, attempt); }
    @Override public String toString() {
        return "SnapshotData{id=" + id + ", stateName=" + stateName + ", status=" + status + "}";
    }
}
