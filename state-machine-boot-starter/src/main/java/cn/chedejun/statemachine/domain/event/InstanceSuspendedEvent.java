package cn.chedejun.statemachine.domain.event;

import cn.chedejun.statemachine.domain.shared.DomainEvent;
import cn.chedejun.statemachine.domain.shared.InstanceId;

public record InstanceSuspendedEvent(InstanceId instanceId) implements DomainEvent {
}
