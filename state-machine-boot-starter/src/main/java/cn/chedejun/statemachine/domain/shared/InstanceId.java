package cn.chedejun.statemachine.domain.shared;
import java.util.UUID;

public record InstanceId(String value) {
    public InstanceId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("InstanceId value cannot be null or empty");
    }
    public static InstanceId generate() { return new InstanceId(UUID.randomUUID().toString()); }
    public static InstanceId of(String value) { return new InstanceId(value); }
    @Override public String toString() { return value; }
}
