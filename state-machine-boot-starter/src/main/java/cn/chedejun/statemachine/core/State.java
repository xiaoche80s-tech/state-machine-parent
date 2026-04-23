package cn.chedejun.statemachine.core;

public class State<C> {
    private final String name;
    private final Action<C> action;

    public State(String name, Action<C> action) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("State name cannot be null or empty");
        this.name = name;
        this.action = action;
    }

    public String getName() { return name; }
    public Action<C> getAction() { return action; }
}
