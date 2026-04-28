package cn.chedejun.statemachine.domain.shared;
import java.util.UUID;

public record DefinitionId(String value) {
    public DefinitionId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("DefinitionId value cannot be null or empty");
    }
    public static DefinitionId generate() { return new DefinitionId(UUID.randomUUID().toString()); }
    public static DefinitionId of(String value) { return new DefinitionId(value); }
    @Override public String toString() { return value; }
}
