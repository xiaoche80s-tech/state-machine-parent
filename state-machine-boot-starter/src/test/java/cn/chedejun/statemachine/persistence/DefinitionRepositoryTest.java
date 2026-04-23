package cn.chedejun.statemachine.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionRepositoryTest extends BaseRepositoryTest {
    private DefinitionRepository repository;

    @BeforeEach void setUpRepo() { repository = new DefinitionRepository(jdbcTemplate, objectMapper); }

    @Test void save_andFindByName_returnsDefinition() {
        List<Map<String, Object>> states = List.of(Map.of("name", "created", "actionClass", "com.example.CreateAction"));
        Map<String, Object> t1 = new java.util.LinkedHashMap<>();
        t1.put("from", "created"); t1.put("to", "paid"); t1.put("conditionExpression", null);
        List<Map<String, Object>> transitions = List.of(t1);
        String id = repository.save("order-process", "v1", states, transitions, "{\"maxAttempts\":3}");
        assertNotNull(id);
        Optional<DefinitionRepository.DefinitionRecord> found = repository.findByNameAndVersion("order-process", "v1");
        assertTrue(found.isPresent());
        assertEquals("order-process", found.get().name());
        assertTrue(found.get().statesJson().contains("created"));
    }

    @Test void findByNameAndVersion_notFound_returnsEmpty() {
        assertTrue(repository.findByNameAndVersion("nonexistent", "v1").isEmpty());
    }

    @Test void findAllByName_returnsAllVersions() {
        repository.save("order-process", "v1", List.of(), List.of(), "{}");
        repository.save("order-process", "v2", List.of(), List.of(), "{}");
        repository.save("other-machine", "v1", List.of(), List.of(), "{}");
        assertEquals(2, repository.findAllByName("order-process").size());
    }

    @Test void findAll_returnsAllDefinitions() {
        repository.save("machine-a", "v1", List.of(), List.of(), "{}");
        repository.save("machine-b", "v1", List.of(), List.of(), "{}");
        assertTrue(repository.findAll().size() >= 2);
    }

    @Test void duplicateNameVersion_throwsException() {
        repository.save("order-process", "v1", List.of(), List.of(), "{}");
        assertThrows(Exception.class, () -> repository.save("order-process", "v1", List.of(), List.of(), "{}"));
    }
}
