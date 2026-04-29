package cn.chedejun.statemachine.domain.instance;

import cn.chedejun.statemachine.core.Context;
import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.State;
import cn.chedejun.statemachine.core.Transition;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.event.*;
import cn.chedejun.statemachine.domain.shared.BusinessId;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.InstanceStatus;
import cn.chedejun.statemachine.domain.shared.StateName;
import cn.chedejun.statemachine.core.StateMachineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StateMachineInstanceTest {

    private StateMachine<Context> machine;
    private InstanceId instanceId;

    @BeforeEach void setUp() {
        machine = new StateMachine<>("test-machine", "v1",
            Arrays.asList(new State<Context>("start", ctx -> {}), new State<Context>("end", ctx -> {})),
            Arrays.asList(new Transition<>("start", "end", ctx -> true)),
            RetryPolicy.exponentialBackoff().maxAttempts(3).build(), Context.class);
        instanceId = InstanceId.generate();
    }

    @Test void newInstance_isRunningWithInitialState() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable("biz-001"));
        assertEquals(InstanceStatus.RUNNING, instance.status());
        assertEquals("start", instance.currentState().value());
        assertEquals(1, instance.getDomainEvents().size());
        assertInstanceOf(InstanceStartedEvent.class, instance.getDomainEvents().get(0));
    }

    @Test void transitionTo_validState_updatesCurrentState() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.transitionTo(StateName.of("end"), machine);
        assertEquals("end", instance.currentState().value());
    }

    @Test void transitionTo_invalidState_throwsException() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        assertThrows(StateMachineException.class, () -> instance.transitionTo(StateName.of("nonexistent"), machine));
    }

    @Test void transitionTo_terminalStatus_throwsException() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.complete();
        assertThrows(StateMachineException.class, () -> instance.transitionTo(StateName.of("end"), machine));
    }

    @Test void markSuspended_changesStatus() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.markSuspended();
        assertEquals(InstanceStatus.SUSPENDED, instance.status());
        assertTrue(instance.getDomainEvents().stream().anyMatch(e -> e instanceof InstanceSuspendedEvent));
    }

    @Test void complete_marksCompleted() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.complete();
        assertEquals(InstanceStatus.COMPLETED, instance.status());
        assertTrue(instance.getDomainEvents().stream().anyMatch(e -> e instanceof InstanceCompletedEvent));
    }

    @Test void fail_marksFailed() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.fail("something went wrong");
        assertEquals(InstanceStatus.FAILED, instance.status());
        assertEquals("something went wrong", instance.errorMessage());
        assertTrue(instance.getDomainEvents().stream().anyMatch(e -> e instanceof InstanceFailedEvent));
    }

    @Test void recordRetry_incrementsCount() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.fail("error");
        instance.recordRetry(machine.getRetryPolicy());
        assertEquals(1, instance.retryCount());
    }

    @Test void recordRetry_nonFailed_throwsException() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        assertThrows(StateMachineException.class, () -> instance.recordRetry(machine.getRetryPolicy()));
    }

    @Test void terminalState_cannotBeChanged() {
        StateMachineInstance instance = new StateMachineInstance(instanceId, machine, BusinessId.ofNullable(null));
        instance.complete();
        assertThrows(StateMachineException.class, instance::markSuspended);
    }
}
