package cn.chedejun.statemachine.domain.shared;

import cn.chedejun.statemachine.domain.event.InstanceStartedEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AggregateRootTest {

    static class TestAggregate extends AggregateRoot<InstanceId> {
        TestAggregate(InstanceId id) { super(id); }
        void triggerStart() { addDomainEvent(new InstanceStartedEvent(id())); }
    }

    @Test void collectsDomainEvents() {
        TestAggregate agg = new TestAggregate(InstanceId.generate());
        agg.triggerStart();
        assertEquals(1, agg.getDomainEvents().size());
        assertInstanceOf(InstanceStartedEvent.class, agg.getDomainEvents().get(0));
    }

    @Test void clearsDomainEvents() {
        TestAggregate agg = new TestAggregate(InstanceId.generate());
        agg.triggerStart();
        agg.clearDomainEvents();
        assertTrue(agg.getDomainEvents().isEmpty());
    }

    @Test void domainEventsListIsUnmodifiable() {
        TestAggregate agg = new TestAggregate(InstanceId.generate());
        assertThrows(UnsupportedOperationException.class, () ->
            agg.getDomainEvents().add(new InstanceStartedEvent(InstanceId.generate())));
    }

    @Test void returnsId() {
        InstanceId id = InstanceId.generate();
        TestAggregate agg = new TestAggregate(id);
        assertEquals(id, agg.id());
    }
}
