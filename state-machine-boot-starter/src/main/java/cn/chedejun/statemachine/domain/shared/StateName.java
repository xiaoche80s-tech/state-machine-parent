package cn.chedejun.statemachine.domain.shared;

import java.util.Objects;

public final class StateName {
    private final String value;

    StateName(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("StateName value cannot be null or empty");
        this.value = value;
    }

    public static StateName of(String value) { return new StateName(value); }
    public String value() { return value; }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateName that = (StateName) o;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
