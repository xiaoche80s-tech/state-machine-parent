package cn.chedejun.statemachine.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.management.dto.*;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Endpoint(id = "state-machines")
public class StateMachineEndpoint {
    private static final Logger log = LoggerFactory.getLogger(StateMachineEndpoint.class);
    private final StateMachineRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private InstanceRepository instanceRepository;
    private SnapshotRepository snapshotRepository;

    public StateMachineEndpoint(StateMachineRegistry registry) { this.registry = registry; }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.instanceRepository = new InstanceRepository(jdbcTemplate);
        this.snapshotRepository = new SnapshotRepository(jdbcTemplate);
    }

    @ReadOperation
    public List<MachineDTO> listMachines() {
        return registry.getMachineNames().stream().map(name -> {
            var versions = registry.getVersions(name);
            long running = 0, failed = 0;
            if (instanceRepository != null) {
                running = instanceRepository.countByMachineNameAndStatus(name, "RUNNING");
                failed = instanceRepository.countByMachineNameAndStatus(name, "FAILED");
            }
            return new MachineDTO(name, versions.size(), running, failed);
        }).toList();
    }

    @ReadOperation
    public List<MachineDefinitionDTO> getVersions(@Selector String name) {
        return registry.getVersions(name).stream().map(r -> {
            try {
                List<Map<String, Object>> states = objectMapper.readValue(r.statesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                List<Map<String, Object>> transitions = objectMapper.readValue(r.transitionsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                Map<String, Object> rp = objectMapper.readValue(r.retryPolicyJson(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), states, transitions, rp, r.registeredAt());
            } catch (Exception e) {
                log.warn("[state-machine] Failed to parse definition for machine {}", name, e);
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), List.of(), List.of(), Map.of(), r.registeredAt());
            }
        }).toList();
    }

    @WriteOperation
    public String retryInstance(@Selector String name, @Selector String instanceId) {
        if (instanceRepository == null) return "Instance repository not available";
        var instance = instanceRepository.findById(instanceId);
        if (instance.isEmpty()) return "Instance not found: " + instanceId;
        if (!"FAILED".equals(instance.get().status())) return "Instance is not FAILED, current status: " + instance.get().status();
        instanceRepository.updateState(instanceId, instance.get().currentState(), "RUNNING", null);
        instanceRepository.setRetryCount(instanceId, 0);
        return "Instance reset to RUNNING. Call stateMachine.retry(instanceId, newContext) to re-execute.";
    }
}
