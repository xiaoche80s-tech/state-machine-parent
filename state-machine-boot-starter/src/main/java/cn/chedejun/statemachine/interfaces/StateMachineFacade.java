package cn.chedejun.statemachine.interfaces;

import cn.chedejun.statemachine.application.InstanceExecutionService;
import cn.chedejun.statemachine.core.Action;
import cn.chedejun.statemachine.core.ExecuteResult;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.shared.BusinessId;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.StateName;

import java.util.function.Consumer;

/**
 * 向后兼容门面：对外保持 execute/retry/resume 签名不变。
 * 委托给 InstanceExecutionService，不直接操作聚合根。
 */
public class StateMachineFacade<C> {

    private final StateMachine<C> machine;
    private final InstanceExecutionService<C> executionService;

    public StateMachineFacade(StateMachine<C> machine, InstanceExecutionService<C> executionService) {
        this.machine = machine;
        this.executionService = executionService;
    }

    public ExecuteResult execute(C context, String businessId) {
        return executionService.execute(machine, context, BusinessId.ofNullable(businessId));
    }

    public void retry(String instanceId) {
        executionService.retry(machine, InstanceId.of(instanceId));
    }

    public void retry(String instanceId, C context) {
        executionService.retryWithCustomContext(machine, InstanceId.of(instanceId), context);
    }

    public void resumeByBusinessId(String businessId, String expectedState, Consumer<C> merger) {
        executionService.resumeByBusinessId(machine, BusinessId.ofNullable(businessId),
            StateName.of(expectedState), merger);
    }

    public void resumeByInstanceId(String instanceId, String expectedState, Consumer<C> merger) {
        executionService.resumeByInstanceId(machine, InstanceId.of(instanceId),
            StateName.of(expectedState), merger);
    }

    /**
     * 推进失败的实例：用新的 Action 替代原失败的 Action 执行
     * 适用于 action 执行失败（非原子操作）后，业务数据已手动修复，需要用新 action 替代并继续流转的场景
     *
     * @param instanceId 实例 ID
     * @param newAction 替代失败 action 的新 action
     * @param contextMerger 用于调整恢复后上下文的合并函数
     */
    public void advance(String instanceId, Action<C> newAction, Consumer<C> contextMerger) {
        executionService.advance(machine, InstanceId.of(instanceId), newAction, contextMerger);
    }

    /**
     * 推进失败的实例（不调整上下文）
     */
    public void advance(String instanceId, Action<C> newAction) {
        executionService.advance(machine, InstanceId.of(instanceId), newAction, ctx -> {});
    }

    public String getName() { return machine.getName(); }
    public String getVersion() { return machine.getVersion(); }
    public StateMachine<C> getMachine() { return machine; }
}
