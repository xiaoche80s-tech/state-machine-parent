package cn.chedejun.statemachine.core;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StateMachineBuilder<C> {

    private final String name;
    private final List<State<C>> states = new ArrayList<>();
    private final List<Transition<C>> transitions = new ArrayList<>();
    private RetryPolicy retryPolicy = RetryPolicy.exponentialBackoff().maxAttempts(3).build();
    private JdbcTemplate jdbcTemplate;
    private StateMachineRegistry registry;
    private Class<C> contextClass;

    private static final AtomicInteger versionCounter = new AtomicInteger(0);

    public StateMachineBuilder(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("State machine name cannot be null or empty");
        this.name = name;
    }

    public static <C> StateMachineBuilder<C> builder(String name) { return new StateMachineBuilder<>(name); }

    public StateMachineBuilder<C> state(String name, Action<C> action) { states.add(new State<>(name, action)); return this; }

    public StateMachineBuilder<C> suspendState(String name, Action<C> action) { states.add(new State<>(name, action, true)); return this; }

    public StateMachineBuilder<C> transition(String from, String to) { return transition(from, to, ctx -> true); }
    public StateMachineBuilder<C> transition(String from, String to, Condition<C> condition) {
        transitions.add(new Transition<>(from, to, condition)); return this;
    }

    public StateMachineBuilder<C> retryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; return this; }
    public StateMachineBuilder<C> contextClass(Class<C> contextClass) { this.contextClass = contextClass; return this; }

    StateMachineBuilder<C> jdbcTemplate(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; return this; }
    StateMachineBuilder<C> registry(StateMachineRegistry registry) { this.registry = registry; return this; }

    public StateMachine<C> build() {
        String version = "v" + versionCounter.incrementAndGet();
        @SuppressWarnings("unchecked")
        Class<C> ctxClass = (Class<C>) (contextClass != null ? contextClass : Context.class);
        StateMachine<C> machine = new StateMachine<>(name, version, states, transitions, retryPolicy, ctxClass);
        if (jdbcTemplate != null) machine.setJdbcTemplate(jdbcTemplate);
        if (registry != null) {
            machine.setRegistry(registry);
            registry.register(machine);
        }
        return machine;
    }
}
