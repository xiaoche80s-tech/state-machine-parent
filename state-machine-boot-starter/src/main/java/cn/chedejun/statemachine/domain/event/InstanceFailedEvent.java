package cn.chedejun.statemachine.domain.event;

import cn.chedejun.statemachine.domain.shared.DomainEvent;
import cn.chedejun.statemachine.domain.shared.InstanceId;

import java.util.Objects;

public final class InstanceFailedEvent implements DomainEvent {
    private final InstanceId instanceId;
    private final String errorMessage;

    public InstanceFailedEvent(InstanceId instanceId, String errorMessage) {
        this.instanceId = instanceId;
        this.errorMessage = errorMessage;
    }

    public InstanceId instanceId() { return instanceId; }
    public String errorMessage() { return errorMessage; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceFailedEvent that = (InstanceFailedEvent) o;
        return Objects.equals(instanceId, that.instanceId) && Objects.equals(errorMessage, that.errorMessage);
    }
    @Override public int hashCode() { return Objects.hash(instanceId, errorMessage); }
    @Override public String toString() { return "InstanceFailedEvent{instanceId=" + instanceId + ", errorMessage='" + errorMessage + "'}"; }
}
