package cn.chedejun.statemachine.domain.shared;
import java.util.UUID;

public record SnapshotId(String value) {
    public SnapshotId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("SnapshotId value cannot be null or empty");
    }
    public static SnapshotId generate() { return new SnapshotId(UUID.randomUUID().toString()); }
    public static SnapshotId of(String value) { return new SnapshotId(value); }
    @Override public String toString() { return value; }
}
