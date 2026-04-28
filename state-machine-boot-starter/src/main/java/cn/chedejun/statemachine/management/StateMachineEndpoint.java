package cn.chedejun.statemachine.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.domain.repository.DefinitionRepository;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.InstanceStatus;
import cn.chedejun.statemachine.domain.shared.MachineName;
import cn.chedejun.statemachine.management.dto.*;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Endpoint(id = "state-machines")
public class StateMachineEndpoint {
    private static final Logger log = LoggerFactory.getLogger(StateMachineEndpoint.class);
    private final StateMachineRegistry registry;
    private final InstanceRepository instanceRepository;
    private final DefinitionRepository definitionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StateMachineEndpoint(StateMachineRegistry registry,
                                 InstanceRepository instanceRepository,
                                 DefinitionRepository definitionRepository) {
        this.registry = registry;
        this.instanceRepository = instanceRepository;
        this.definitionRepository = definitionRepository;
    }

    @ReadOperation
    public List<MachineDTO> listMachines() {
        return registry.getMachineNames().stream().map(name -> {
            var versions = definitionRepository.findAllByName(MachineName.of(name));
            long running = 0, failed = 0;
            if (instanceRepository != null) {
                running = instanceRepository.countByMachineNameAndStatus(MachineName.of(name), InstanceStatus.RUNNING);
                failed = instanceRepository.countByMachineNameAndStatus(MachineName.of(name), InstanceStatus.FAILED);
            }
            return new MachineDTO(name, versions.size(), running, failed);
        }).toList();
    }

    @ReadOperation
    public List<MachineDefinitionDTO> getVersions(@Selector String name) {
        return definitionRepository.findAllByName(MachineName.of(name)).stream().map(r -> {
            try {
                List<Map<String, Object>> states = objectMapper.readValue(r.statesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                List<Map<String, Object>> transitions = objectMapper.readValue(r.transitionsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                Map<String, Object> rp = objectMapper.readValue(r.retryPolicyJson(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), states, transitions, rp, r.registeredAt());
            } catch (Exception e) {
                log.warn("[state-machine] 解析状态机定义失败 {}", name, e);
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), List.of(), List.of(), Map.of(), r.registeredAt());
            }
        }).toList();
    }

    @WriteOperation
    public String retryInstance(@Selector String name, @Selector String instanceId) {
        if (instanceRepository == null) return "Instance repository not available";
        var instance = instanceRepository.findById(InstanceId.of(instanceId));
        if (instance.isEmpty()) return "Instance not found: " + instanceId;
        if (instance.get().status() != InstanceStatus.FAILED) return "Instance is not FAILED, current status: " + instance.get().status();
        return "Instance reset to RUNNING. Call stateMachine.retry(instanceId, newContext) to re-execute.";
    }
}
