package cn.chedejun.statemachine.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstanceRepositoryTest extends BaseRepositoryTest {
    private InstanceRepository repository;

    @BeforeEach void setUpRepo() { repository = new InstanceRepository(jdbcTemplate); }

    @Test void create_setsRunningStatus() {
        String id = repository.create("def-1", "order-process", "v1", "created");
        assertNotNull(id);
        var r = repository.findById(id);
        assertTrue(r.isPresent());
        assertEquals("RUNNING", r.get().status());
        assertEquals("created", r.get().currentState());
    }

    @Test void updateState_changesStateAndStatus() {
        String id = repository.create("def-1", "order-process", "v1", "created");
        repository.updateState(id, "paid", "COMPLETED", null);
        var r = repository.findById(id);
        assertEquals("paid", r.get().currentState());
        assertEquals("COMPLETED", r.get().status());
    }

    @Test void incrementRetry_incrementsCounter() {
        String id = repository.create("def-1", "order-process", "v1", "created");
        repository.incrementRetry(id, 1, java.time.Instant.now().plusSeconds(5));
        var r = repository.findById(id);
        assertEquals(1, r.get().retryCount());
        assertEquals("RUNNING", r.get().status());
    }

    @Test void findByMachineName_returnsFiltered() {
        repository.create("def-1", "machine-a", "v1", "s1");
        repository.create("def-2", "machine-b", "v1", "s1");
        var results = repository.findByMachineName("machine-a", 0, 10);
        assertEquals(1, results.size());
    }

    @Test void countByMachineNameAndStatus_returnsCorrectCount() {
        String id1 = repository.create("def-1", "order-process", "v1", "s1");
        repository.create("def-2", "order-process", "v1", "s1");
        repository.updateState(id1, "s2", "COMPLETED", null);
        assertEquals(2L, repository.countByMachineNameAndStatus("order-process", null));
        assertEquals(1L, repository.countByMachineNameAndStatus("order-process", "COMPLETED"));
    }
}
