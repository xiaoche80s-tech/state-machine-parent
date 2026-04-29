package cn.chedejun.statemachine.domain.shared;

import java.util.Objects;
import java.util.UUID;

public final class SnapshotId {
    private final String value;

    SnapshotId(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("SnapshotId value cannot be null or empty");
        this.value = value;
    }

    public static SnapshotId generate() { return new SnapshotId(UUID.randomUUID().toString()); }
    public static SnapshotId of(String value) { return new SnapshotId(value); }
    public String value() { return value; }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnapshotId that = (SnapshotId) o;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
