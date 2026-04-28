package cn.chedejun.statemachine.domain.shared;

public record StateName(String value) {
    public StateName {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("StateName value cannot be null or empty");
    }
    public static StateName of(String value) { return new StateName(value); }
    @Override public String toString() { return value; }
}
