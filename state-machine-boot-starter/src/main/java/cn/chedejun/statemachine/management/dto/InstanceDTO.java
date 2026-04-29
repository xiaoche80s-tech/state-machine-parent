package cn.chedejun.statemachine.management.dto;

import java.time.Instant;
import java.util.Objects;

public final class InstanceDTO {
    private final String id;
    private final String machineName;
    private final String definitionVersion;
    private final String currentState;
    private final String status;
    private final String businessId;
    private final int retryCount;
    private final String errorMessage;
    private final Instant createdAt;
    private final Instant updatedAt;

    public InstanceDTO(String id, String machineName, String definitionVersion,
                       String currentState, String status, String businessId,
                       int retryCount, String errorMessage, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.machineName = machineName;
        this.definitionVersion = definitionVersion;
        this.currentState = currentState;
        this.status = status;
        this.businessId = businessId;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String id() { return id; }
    public String machineName() { return machineName; }
    public String definitionVersion() { return definitionVersion; }
    public String currentState() { return currentState; }
    public String status() { return status; }
    public String businessId() { return businessId; }
    public int retryCount() { return retryCount; }
    public String errorMessage() { return errorMessage; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceDTO that = (InstanceDTO) o;
        return retryCount == that.retryCount && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id, retryCount); }
    @Override public String toString() {
        return "InstanceDTO{id='" + id + "', status='" + status + "'}";
    }
}
