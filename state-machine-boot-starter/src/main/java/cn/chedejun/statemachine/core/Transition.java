package cn.chedejun.statemachine.core;

public class Transition<C> {
    private final String from;
    private final String to;
    private final Condition<C> condition;

    public Transition(String from, String to, Condition<C> condition) {
        if (from == null || from.isBlank()) throw new IllegalArgumentException("Transition 'from' cannot be null or empty");
        if (to == null || to.isBlank()) throw new IllegalArgumentException("Transition 'to' cannot be null or empty");
        this.from = from;
        this.to = to;
        this.condition = condition;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public Condition<C> getCondition() { return condition; }
}
