package cn.chedejun.statemachine.domain.event;

import cn.chedejun.statemachine.domain.shared.DomainEvent;
import cn.chedejun.statemachine.domain.shared.InstanceId;

import java.util.Objects;

public final class InstanceStartedEvent implements DomainEvent {
    private final InstanceId instanceId;

    public InstanceStartedEvent(InstanceId instanceId) {
        this.instanceId = instanceId;
    }

    public InstanceId instanceId() { return instanceId; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceStartedEvent that = (InstanceStartedEvent) o;
        return Objects.equals(instanceId, that.instanceId);
    }
    @Override public int hashCode() { return Objects.hash(instanceId); }
    @Override public String toString() { return "InstanceStartedEvent{instanceId=" + instanceId + "}"; }
}
