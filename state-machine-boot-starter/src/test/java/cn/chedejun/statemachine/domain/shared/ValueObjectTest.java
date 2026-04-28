package cn.chedejun.statemachine.domain.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ValueObjectTest {

    @Test void instanceId_generate_returnsNonNull() {
        InstanceId id = InstanceId.generate();
        assertNotNull(id);
        assertFalse(id.value().isBlank());
    }

    @Test void instanceId_of_validValue_returnsInstance() {
        InstanceId id = InstanceId.of("test-id-123");
        assertEquals("test-id-123", id.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t"})
    void instanceId_rejectsBlank(String blank) {
        assertThrows(IllegalArgumentException.class, () -> new InstanceId(blank));
    }

    @Test void instanceId_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new InstanceId(null));
    }

    @Test void instanceId_equalsAndHashCode() {
        InstanceId a = InstanceId.of("same-id");
        InstanceId b = InstanceId.of("same-id");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test void definitionId_generate_returnsNonNull() { assertNotNull(DefinitionId.generate()); }
    @Test void definitionId_rejectsBlank() { assertThrows(IllegalArgumentException.class, () -> new DefinitionId("")); }

    @Test void snapshotId_generate_returnsNonNull() { assertNotNull(SnapshotId.generate()); }
    @Test void snapshotId_rejectsBlank() { assertThrows(IllegalArgumentException.class, () -> new SnapshotId("")); }

    @Test void businessId_allowsNull() { assertNull(BusinessId.ofNullable(null).value()); }
    @Test void businessId_acceptsValue() { assertEquals("ORD-001", BusinessId.of("ORD-001").value()); }

    @Test void machineName_rejectsBlank() { assertThrows(IllegalArgumentException.class, () -> new MachineName("")); }
    @Test void stateName_rejectsBlank() { assertThrows(IllegalArgumentException.class, () -> new StateName("")); }

    @Test void instanceStatus_hasAllValues() {
        assertEquals(4, InstanceStatus.values().length);
        assertNotNull(InstanceStatus.RUNNING); assertNotNull(InstanceStatus.SUSPENDED);
        assertNotNull(InstanceStatus.COMPLETED); assertNotNull(InstanceStatus.FAILED);
    }

    @Test void executionStatus_hasAllValues() {
        assertEquals(2, ExecutionStatus.values().length);
        assertNotNull(ExecutionStatus.SUCCESS); assertNotNull(ExecutionStatus.FAILED);
    }
}
