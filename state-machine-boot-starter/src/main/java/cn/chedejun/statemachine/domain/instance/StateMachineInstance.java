package cn.chedejun.statemachine.domain.instance;

import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.StateMachineException;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.event.*;
import cn.chedejun.statemachine.domain.shared.*;

public class StateMachineInstance extends AggregateRoot<InstanceId> {

    private DefinitionId definitionId;
    private MachineName machineName;
    private StateName currentState;
    private InstanceStatus status;
    private BusinessId businessId;
    private int retryCount;
    private String errorMessage;

    public StateMachineInstance(InstanceId id, StateMachine<?> machine, BusinessId businessId) {
        super(id);
        this.definitionId = DefinitionId.of("unknown");
        this.machineName = MachineName.of(machine.getName());
        this.currentState = StateName.of(machine.getStates().get(0).getName());
        this.status = InstanceStatus.RUNNING;
        this.businessId = businessId;
        this.retryCount = 0;
        addDomainEvent(new InstanceStartedEvent(id));
    }

    public void setDefinitionId(DefinitionId definitionId) { this.definitionId = definitionId; }
    public DefinitionId definitionId() { return definitionId; }
    public MachineName machineName() { return machineName; }
    public StateName currentState() { return currentState; }
    public InstanceStatus status() { return status; }
    public BusinessId businessId() { return businessId; }
    public int retryCount() { return retryCount; }
    public String errorMessage() { return errorMessage; }

    public void transitionTo(StateName target, StateMachine<?> machine) {
        validateNotTerminal();
        if (!machine.hasState(target))
            throw new StateMachineException(String.format("State '%s' not found in machine '%s'", target.value(), machine.getName()));
        this.currentState = target;
    }

    public void markSuspended() {
        validateNotTerminal();
        this.status = InstanceStatus.SUSPENDED;
        addDomainEvent(new InstanceSuspendedEvent(id()));
    }

    public void recordRetry(RetryPolicy retryPolicy) {
        if (status != InstanceStatus.FAILED)
            throw new StateMachineException("Can only retry FAILED instances, current status: " + status);
        if (retryCount >= retryPolicy.getMaxAttempts())
            throw new StateMachineException(String.format("Retry count %d exceeds max attempts %d", retryCount, retryPolicy.getMaxAttempts()));
        this.retryCount++;
        this.status = InstanceStatus.RUNNING;
    }

    public void resetRetryCount() { this.retryCount = 0; }

    public void complete() {
        validateNotTerminal();
        this.status = InstanceStatus.COMPLETED;
        addDomainEvent(new InstanceCompletedEvent(id()));
    }

    public void fail(String error) {
        validateNotTerminal();
        this.status = InstanceStatus.FAILED;
        this.errorMessage = error;
        addDomainEvent(new InstanceFailedEvent(id(), error));
    }

    private void validateNotTerminal() {
        if (status == InstanceStatus.COMPLETED || status == InstanceStatus.FAILED)
            throw new StateMachineException("Instance is in terminal status: " + status);
    }
}
