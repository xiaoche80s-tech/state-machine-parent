package cn.chedejun.statemachine.domain.shared;

public record BusinessId(String value) {
    public BusinessId { /* allows null */ }
    public static BusinessId ofNullable(String value) { return new BusinessId(value); }
    public static BusinessId of(String value) { return new BusinessId(value); }
    @Override public String toString() { return value != null ? value : ""; }
}
