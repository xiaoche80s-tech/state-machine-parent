package cn.chedejun.statemachine.domain.snapshot;

import cn.chedejun.statemachine.domain.shared.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionSnapshotTest {

    private final SnapshotId id = SnapshotId.generate();
    private final InstanceId instanceId = InstanceId.generate();

    @Test void createSuccess_hasCorrectFields() {
        ExecutionSnapshot snapshot = ExecutionSnapshot.createSuccess(id, instanceId, StateName.of("validate"),
            "{\"key\":\"value\"}", "{\"result\":true}", 1);
        assertEquals(instanceId, snapshot.instanceId());
        assertEquals("validate", snapshot.stateName().value());
        assertEquals(ExecutionStatus.SUCCESS, snapshot.status());
        assertEquals("NODE", snapshot.snapshotType());
        assertNull(snapshot.errorMessage());
    }

    @Test void createFailed_hasErrorMessage() {
        ExecutionSnapshot snapshot = ExecutionSnapshot.createFailed(id, instanceId, StateName.of("validate"),
            "{\"key\":\"value\"}", "timeout error", 1);
        assertEquals(ExecutionStatus.FAILED, snapshot.status());
        assertEquals("timeout error", snapshot.errorMessage());
        assertEquals("NODE", snapshot.snapshotType());
    }

    @Test void createRoute_recordsTargetState() {
        ExecutionSnapshot snapshot = ExecutionSnapshot.createRoute(id, instanceId, StateName.of("validate"),
            "{\"key\":\"value\"}", StateName.of("process"));
        assertEquals("ROUTE", snapshot.snapshotType());
        assertEquals(ExecutionStatus.SUCCESS, snapshot.status());
        assertEquals("\"process\"", snapshot.outputJson());
    }

    @Test void createRouteFailed_recordsError() {
        ExecutionSnapshot snapshot = ExecutionSnapshot.createRouteFailed(id, instanceId, StateName.of("validate"),
            "{\"key\":\"value\"}", "no matching transition");
        assertEquals("ROUTE", snapshot.snapshotType());
        assertEquals(ExecutionStatus.FAILED, snapshot.status());
        assertEquals("no matching transition", snapshot.errorMessage());
    }

    @Test void snapshotIsImmutable() {
        ExecutionSnapshot snapshot = ExecutionSnapshot.createSuccess(id, instanceId, StateName.of("start"),
            "{}", "{}", 1);
        assertNotNull(snapshot.executedAt());
    }
}
