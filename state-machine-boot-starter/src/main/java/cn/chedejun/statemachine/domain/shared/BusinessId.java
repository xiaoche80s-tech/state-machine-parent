package cn.chedejun.statemachine.domain.shared;

import java.util.Objects;

public final class BusinessId {
    private final String value;

    BusinessId(String value) {
        this.value = value;
    }

    public static BusinessId ofNullable(String value) { return new BusinessId(value); }
    public static BusinessId of(String value) { return new BusinessId(value); }
    public String value() { return value; }
    @Override public String toString() { return value != null ? value : ""; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessId that = (BusinessId) o;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
