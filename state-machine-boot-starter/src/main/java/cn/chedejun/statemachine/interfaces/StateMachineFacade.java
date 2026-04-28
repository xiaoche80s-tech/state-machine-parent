package cn.chedejun.statemachine.interfaces;

import cn.chedejun.statemachine.application.InstanceExecutionService;
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

    public String getName() { return machine.getName(); }
    public String getVersion() { return machine.getVersion(); }
    public StateMachine<C> getMachine() { return machine; }
}
