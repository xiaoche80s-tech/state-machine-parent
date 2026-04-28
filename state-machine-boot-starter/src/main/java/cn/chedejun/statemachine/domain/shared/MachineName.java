package cn.chedejun.statemachine.domain.shared;

public record MachineName(String value) {
    public MachineName {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("MachineName value cannot be null or empty");
    }
    public static MachineName of(String value) { return new MachineName(value); }
    @Override public String toString() { return value; }
}
