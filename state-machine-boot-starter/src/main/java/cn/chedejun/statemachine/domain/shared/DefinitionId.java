package cn.chedejun.statemachine.domain.shared;

import java.util.Objects;
import java.util.UUID;

public final class DefinitionId {
    private final String value;

    DefinitionId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("DefinitionId value cannot be null or empty");
        this.value = value;
    }

    public static DefinitionId generate() { return new DefinitionId(UUID.randomUUID().toString()); }
    public static DefinitionId of(String value) { return new DefinitionId(value); }
    public String value() { return value; }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefinitionId that = (DefinitionId) o;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
