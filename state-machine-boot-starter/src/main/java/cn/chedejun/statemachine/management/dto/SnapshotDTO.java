package cn.chedejun.statemachine.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

public final class SnapshotDTO {
    private final String id;
    private final String stateName;
    private final String input;
    private final String output;
    private final String status;
    private final String errorMessage;
    private final int attempt;
    private final String snapshotType;
    private final Instant executedAt;

    public SnapshotDTO(String id, String stateName, String input, String output,
                       String status, String errorMessage, int attempt,
                       String snapshotType, Instant executedAt) {
        this.id = id;
        this.stateName = stateName;
        this.input = input;
        this.output = output;
        this.status = status;
        this.errorMessage = errorMessage;
        this.attempt = attempt;
        this.snapshotType = snapshotType;
        this.executedAt = executedAt;
    }

    @JsonProperty public String id() { return id; }
    @JsonProperty public String stateName() { return stateName; }
    @JsonProperty public String input() { return input; }
    @JsonProperty public String output() { return output; }
    @JsonProperty public String status() { return status; }
    @JsonProperty public String errorMessage() { return errorMessage; }
    @JsonProperty public int attempt() { return attempt; }
    @JsonProperty public String snapshotType() { return snapshotType; }
    @JsonProperty public Instant executedAt() { return executedAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnapshotDTO that = (SnapshotDTO) o;
        return attempt == that.attempt && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id, attempt); }
    @Override public String toString() {
        return "SnapshotDTO{id='" + id + "', stateName='" + stateName + "'}";
    }
}
