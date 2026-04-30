package cn.chedejun.statemachine.domain.engine;

import cn.chedejun.statemachine.core.Action;
import cn.chedejun.statemachine.core.Condition;
import cn.chedejun.statemachine.core.Context;
import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.State;
import cn.chedejun.statemachine.core.Transition;
import cn.chedejun.statemachine.domain.shared.StateName;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 不可变领域对象：承载状态定义、转换规则、重试策略。
 * 不继承 AggregateRoot（非运行时业务实体，无生命周期变化，不产生领域事件）。
 */
public class StateMachine<C> {

    private final String name;
    private final String version;
    private final List<State<C>> states;
    private final List<Transition<C>> transitions;
    private final RetryPolicy retryPolicy;
    private final Class<C> contextClass;

    public StateMachine(String name, String version, List<State<C>> states,
                        List<Transition<C>> transitions, RetryPolicy retryPolicy, Class<C> contextClass) {
        if (states == null || states.isEmpty())
            throw new IllegalArgumentException("StateMachine must have at least one state");
        if (transitions == null || transitions.isEmpty())
            throw new IllegalArgumentException("StateMachine must have at least one transition");

        this.name = name;
        this.version = version;
        this.states = Collections.unmodifiableList(states);
        this.transitions = Collections.unmodifiableList(transitions);
        this.retryPolicy = retryPolicy;
        this.contextClass = contextClass;
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public List<State<C>> getStates() { return states; }
    public List<Transition<C>> getTransitions() { return transitions; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public Class<C> getContextClass() { return contextClass; }

    public Optional<State<C>> findState(String name) {
        return states.stream().filter(s -> s.getName().equals(name)).findFirst();
    }

    /** 返回初始状态（即按顺序添加的第一个状态）。由于构造旹法已检验 states 非空，此方法必然有返回値。 */
    public State<C> getInitialState() {
        return states.get(0);
    }

    public boolean hasState(StateName stateName) {
        return states.stream().anyMatch(s -> s.getName().equals(stateName.value()));
    }

    public Optional<String> findNextState(C context, String fromState) {
        for (Transition<C> t : transitions) {
            if (t.getFrom().equals(fromState)) {
                if (t.getCondition() == null || t.getCondition().test(context))
                    return Optional.of(t.getTo());
            }
        }
        return Optional.empty();
    }

    public boolean hasOutgoingTransitions(String fromState) {
        return transitions.stream().anyMatch(t -> t.getFrom().equals(fromState));
    }
}
