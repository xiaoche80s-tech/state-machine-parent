package cn.chedejun.statemachine.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty public String id() { return id; }
    @JsonProperty public String machineName() { return machineName; }
    @JsonProperty public String definitionVersion() { return definitionVersion; }
    @JsonProperty public String currentState() { return currentState; }
    @JsonProperty public String status() { return status; }
    @JsonProperty public String businessId() { return businessId; }
    @JsonProperty public int retryCount() { return retryCount; }
    @JsonProperty public String errorMessage() { return errorMessage; }
    @JsonProperty public Instant createdAt() { return createdAt; }
    @JsonProperty public Instant updatedAt() { return updatedAt; }
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
