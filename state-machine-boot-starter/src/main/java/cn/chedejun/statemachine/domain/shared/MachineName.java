package cn.chedejun.statemachine.domain.shared;

import java.util.Objects;

public final class MachineName {
    private final String value;

    MachineName(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("MachineName value cannot be null or empty");
        this.value = value;
    }

    public static MachineName of(String value) { return new MachineName(value); }
    public String value() { return value; }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MachineName that = (MachineName) o;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
