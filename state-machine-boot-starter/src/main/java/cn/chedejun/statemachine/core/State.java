package cn.chedejun.statemachine.core;

public class State<C> {
    private final String name;
    private final Action<C> action;
    private final boolean suspended;

    public State(String name, Action<C> action) {
        this(name, action, false);
    }

    public State(String name, Action<C> action, boolean suspended) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("State name cannot be null or empty");
        this.name = name;
        this.action = action;
        this.suspended = suspended;
    }

    public String getName() { return name; }
    public Action<C> getAction() { return action; }
    public boolean isSuspended() { return suspended; }
}
