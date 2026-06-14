package cn.chedejun.statemachine.core;

public class State<C> {
    private final String name;
    private final Action<C> action;
    private final boolean suspended;
    private final Condition<C> resumeCondition;

    public State(String name, Action<C> action) {
        this(name, action, false, null);
    }

    public State(String name, Action<C> action, boolean suspended) {
        this(name, action, suspended, null);
    }

    public State(String name, Action<C> action, boolean suspended, Condition<C> resumeCondition) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("State name cannot be null or empty");
        this.name = name;
        this.action = action;
        this.suspended = suspended;
        this.resumeCondition = resumeCondition;
    }

    public String getName() { return name; }
    public Action<C> getAction() { return action; }
    public boolean isSuspended() { return suspended; }
    public Condition<C> getResumeCondition() { return resumeCondition; }
    public boolean hasResumeCondition() { return resumeCondition != null; }
}
