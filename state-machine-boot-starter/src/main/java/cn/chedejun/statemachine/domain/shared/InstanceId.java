package cn.chedejun.statemachine.domain.shared;

import java.util.Objects;
import java.util.UUID;

public final class InstanceId {
    private final String value;

    InstanceId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("InstanceId value cannot be null or empty");
        this.value = value;
    }

    public static InstanceId generate() { return new InstanceId(UUID.randomUUID().toString()); }
    public static InstanceId of(String value) { return new InstanceId(value); }
    public String value() { return value; }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceId that = (InstanceId) o;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
