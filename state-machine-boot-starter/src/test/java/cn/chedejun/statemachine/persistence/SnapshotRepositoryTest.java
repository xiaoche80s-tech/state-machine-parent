package cn.chedejun.statemachine.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotRepositoryTest extends BaseRepositoryTest {
    private SnapshotRepository repository;
    private InstanceRepository instanceRepository;

    @BeforeEach void setUpRepo() { repository = new SnapshotRepository(jdbcTemplate); instanceRepository = new InstanceRepository(jdbcTemplate); }

    @Test void save_createsSnapshot() {
        String instanceId = instanceRepository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "created"));
        String sid = repository.save(instanceId, "created", "{\"key\":\"value\"}", "{\"result\":\"ok\"}", "SUCCESS", null, 1, "NODE");
        assertNotNull(sid);
        var s = repository.findById(sid);
        assertEquals("created", s.stateName());
        assertEquals("SUCCESS", s.status());
        assertEquals(1, s.attempt());
    }

    @Test void save_withError_recordsErrorMessage() {
        String instanceId = instanceRepository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "created"));
        repository.save(instanceId, "created", "{}", null, "FAILED", "NullPointerException", 1, "NODE");
        var snaps = repository.findByInstanceId(instanceId);
        assertEquals(1, snaps.size());
        assertEquals("FAILED", snaps.get(0).status());
        assertEquals("NullPointerException", snaps.get(0).errorMessage());
    }

    @Test void findByInstanceId_returnsOrderedByTimeAndAttempt() {
        String instanceId = instanceRepository.create(new InstanceRepository.CreateInstanceParams("def-1", "order-process", "v1", "created"));
        repository.save(instanceId, "step1", "{}", "{}", "FAILED", "err", 1, "NODE");
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        repository.save(instanceId, "step1", "{}", "{}", "SUCCESS", null, 2, "NODE");
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        repository.save(instanceId, "step2", "{}", "{}", "SUCCESS", null, 1, "NODE");
        var snaps = repository.findByInstanceId(instanceId);
        assertEquals(3, snaps.size());
        assertEquals(1, snaps.get(0).attempt());
        assertEquals(2, snaps.get(1).attempt());
        assertEquals(1, snaps.get(2).attempt());
    }
}
