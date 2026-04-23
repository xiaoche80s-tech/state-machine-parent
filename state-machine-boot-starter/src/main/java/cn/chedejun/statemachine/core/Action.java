package cn.chedejun.statemachine.core;

/**
 * 状态步骤执行业务逻辑。
 * 结果通过 context.put() 写入，自动记录到快照。
 * @param <C> Context 类型（共享上下文）
 */
@FunctionalInterface
public interface Action<C> {
    void execute(C context) throws Exception;
}
