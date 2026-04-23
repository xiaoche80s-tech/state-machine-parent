package cn.chedejun.statemachine.core;

@FunctionalInterface
public interface Condition<C> {
    boolean test(C context);
}
