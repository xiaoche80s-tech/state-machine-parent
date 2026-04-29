package cn.chedejun.statemachine.domain.data;

import cn.chedejun.statemachine.domain.shared.*;

import java.time.Instant;
import java.util.Objects;

public final class InstanceData {
    private final InstanceId id;
    private final DefinitionId definitionId;
    private final MachineName machineName;
    private final String definitionVersion;
    private final StateName currentState;
    private final BusinessId businessId;
    private final InstanceStatus status;
    private final int retryCount;
    private final Instant nextRetryAt;
    private final String errorMessage;
    private final Instant createdAt;
    private final Instant updatedAt;

    public InstanceData(InstanceId id, DefinitionId definitionId, MachineName machineName,
                        String definitionVersion, StateName currentState, BusinessId businessId,
                        InstanceStatus status, int retryCount, Instant nextRetryAt,
                        String errorMessage, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.definitionId = definitionId;
        this.machineName = machineName;
        this.definitionVersion = definitionVersion;
        this.currentState = currentState;
        this.businessId = businessId;
        this.status = status;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InstanceData newInstance(InstanceId id, DefinitionId definitionId, MachineName machineName,
                                            String version, StateName initialState, BusinessId businessId) {
        return new InstanceData(id, definitionId, machineName, version, initialState,
            businessId, InstanceStatus.RUNNING, 0, null, null, Instant.now(), Instant.now());
    }

    public InstanceData withUpdatedState(StateName newState, InstanceStatus newStatus, String newError) {
        return new InstanceData(id, definitionId, machineName, definitionVersion, newState,
            businessId, newStatus, retryCount, nextRetryAt, newError, createdAt, Instant.now());
    }

    public InstanceData withIncrementedRetry(int newRetryCount, Instant newNextRetryAt) {
        return new InstanceData(id, definitionId, machineName, definitionVersion, currentState,
            businessId, InstanceStatus.RUNNING, newRetryCount, newNextRetryAt, errorMessage, createdAt, Instant.now());
    }

    public InstanceId id() { return id; }
    public DefinitionId definitionId() { return definitionId; }
    public MachineName machineName() { return machineName; }
    public String definitionVersion() { return definitionVersion; }
    public StateName currentState() { return currentState; }
    public BusinessId businessId() { return businessId; }
    public InstanceStatus status() { return status; }
    public int retryCount() { return retryCount; }
    public Instant nextRetryAt() { return nextRetryAt; }
    public String errorMessage() { return errorMessage; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceData that = (InstanceData) o;
        return retryCount == that.retryCount && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id, retryCount); }
    @Override public String toString() {
        return "InstanceData{id=" + id + ", status=" + status + ", currentState=" + currentState + "}";
    }
}
