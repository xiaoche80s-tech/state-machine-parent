package cn.chedejun.statemachine.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.application.InstanceExecutionService;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.domain.shared.BusinessId;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.InstanceStatus;
import cn.chedejun.statemachine.domain.shared.MachineName;
import cn.chedejun.statemachine.domain.shared.StateName;
import cn.chedejun.statemachine.management.dto.InstanceDTO;
import cn.chedejun.statemachine.management.dto.MachineDefinitionDTO;
import cn.chedejun.statemachine.management.dto.SnapshotDTO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Controller
@RequestMapping("/statemachine")
public class ConsoleController {
    private static final Logger log = LoggerFactory.getLogger(ConsoleController.class);

    @GetMapping({"", "/"})
    public String index() {
        return "forward:/statemachine/index.html";
    }
    private final StateMachineRegistry registry;
    private final InstanceRepository instanceRepository;
    private final SnapshotRepository snapshotRepository;
    private final InstanceExecutionService<Object> executionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public ConsoleController(StateMachineRegistry registry,
                              InstanceRepository instanceRepository,
                              SnapshotRepository snapshotRepository,
                              InstanceExecutionService<Object> executionService) {
        this.registry = registry;
        this.instanceRepository = instanceRepository;
        this.snapshotRepository = snapshotRepository;
        this.executionService = executionService;
    }

    @GetMapping("/api/machines") @ResponseBody
    public List<Map<String, Object>> listMachines() {
        return registry.getMachineNames().stream().map(name -> {
            var versions = registry.getVersions(name);
            MachineName machineName = MachineName.of(name);
            return Map.<String, Object>of(
                "name", name, "versionCount", versions.size(),
                "runningInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.RUNNING),
                "failedInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.FAILED),
                "suspendedInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.SUSPENDED));
        }).toList();
    }

    @GetMapping("/api/machines/{name}/versions") @ResponseBody
    public List<MachineDefinitionDTO> getVersions(@PathVariable String name) {
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
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), List.of(), List.of(), Map.of(), r.registeredAt());
            }
        }).toList();
    }

    @GetMapping("/api/machines/{name}/instances") @ResponseBody
    public Map<String, Object> getInstances(@PathVariable String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessId,
            @RequestParam(required = false) String instanceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MachineName machineName = MachineName.of(name);
        InstanceStatus statusEnum = status != null && !status.isEmpty() ? InstanceStatus.valueOf(status) : null;
        BusinessId businessIdObj = businessId != null && !businessId.isEmpty() ? BusinessId.of(businessId) : null;
        InstanceId instanceIdObj = instanceId != null && !instanceId.isEmpty() ? InstanceId.of(instanceId) : null;

        boolean hasFilters = statusEnum != null || businessIdObj != null || instanceIdObj != null;
        var records = hasFilters
            ? instanceRepository.findByMachineNameWithFilters(machineName, statusEnum, businessIdObj, instanceIdObj, page * size, size)
            : instanceRepository.findByMachineName(machineName, page * size, size);
        long total = hasFilters
            ? instanceRepository.countByMachineNameWithFilters(machineName, statusEnum, businessIdObj, instanceIdObj)
            : instanceRepository.countByMachineNameWithFilters(machineName, null, null, null);
        var dtos = records.stream().map(r -> new InstanceDTO(r.id().value(), r.machineName().value(), r.definitionVersion(),
            r.currentState().value(), r.status().name(), r.businessId() != null ? r.businessId().value() : null, r.retryCount(), r.errorMessage(), r.createdAt(), r.updatedAt())).toList();
        return Map.of("instances", dtos, "total", total, "page", page, "size", size);
    }

    @GetMapping("/api/instances/{id}") @ResponseBody
    public Map<String, Object> getInstanceDetail(@PathVariable String id) {
        var inst = instanceRepository.findById(InstanceId.of(id));
        if (inst.isEmpty()) return Map.of("error", "Instance not found");
        var snaps = snapshotRepository.findByInstanceId(InstanceId.of(id)).stream()
            .map(s -> new SnapshotDTO(s.id().value(), s.stateName().value(), s.inputJson(), s.outputJson(),
                s.status().name(), s.errorMessage(), s.attempt(), s.snapshotType(), s.executedAt())).toList();
        var r = inst.get();
        var dto = new InstanceDTO(r.id().value(), r.machineName().value(), r.definitionVersion(),
            r.currentState().value(), r.status().name(), r.businessId() != null ? r.businessId().value() : null, r.retryCount(),
            r.errorMessage(), r.createdAt(), r.updatedAt());
        return Map.of("instance", dto, "snapshots", snaps);
    }

    @PostMapping("/api/instances/{id}/resume") @ResponseBody
    public Map<String, Object> resumeInstance(@PathVariable String id,
            @RequestBody Map<String, String> params) {
        var inst = instanceRepository.findById(InstanceId.of(id));
        if (inst.isEmpty()) return Map.of("success", false, "error", "Instance not found");
        String expectedState = params.get("expectedCurrentState");
        if (expectedState == null || expectedState.isBlank()) {
            return Map.of("success", false, "error", "Missing expectedCurrentState");
        }
        var machineOpt = registry.getLatest(inst.get().machineName().value());
        if (machineOpt.isEmpty()) return Map.of("success", false, "error", "State machine not found: " + inst.get().machineName().value());
        try {
            String contextJson = params.get("contextJson");
            @SuppressWarnings("unchecked")
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();

            if (contextJson != null && !contextJson.isBlank()) {
                final String cj = contextJson;
                executionService.resumeByInstanceId(m, InstanceId.of(id),
                    StateName.of(expectedState), ctx -> {
                        try {
                            objectMapper.readerForUpdating(ctx).readValue(cj);
                        } catch (Exception e) {
                            log.error("[state-machine] 恢复实例 {} 时解析 contextJson 失败", id, e);
                            throw new RuntimeException("解析 contextJson 失败: " + e.getMessage(), e);
                        }
                    });
            } else {
                executionService.resumeByInstanceId(m, InstanceId.of(id),
                    StateName.of(expectedState), c -> {});
            }
            var updated = instanceRepository.findById(InstanceId.of(id));
            return Map.of("success", true, "message", "已恢复执行", "currentState",
                updated.map(i -> i.currentState().value()).orElse("unknown"));
        } catch (Exception e) {
            log.error("[state-machine] 恢复实例失败 id={}", id, e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/api/instances/{id}/retry") @ResponseBody
    public Map<String, String> retryInstance(@PathVariable String id) {
        var inst = instanceRepository.findById(InstanceId.of(id));
        if (inst.isEmpty()) return Map.of("error", "Instance not found");
        var machineOpt = registry.getLatest(inst.get().machineName().value());
        if (machineOpt.isEmpty()) return Map.of("error", "State machine not found: " + inst.get().machineName().value());
        try {
            @SuppressWarnings("unchecked")
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();
            executionService.retry(m, InstanceId.of(id));
            var updated = instanceRepository.findById(InstanceId.of(id));
            return Map.of("message", "Instance re-executed from state: " + updated.map(i -> i.currentState().value()).orElse("unknown"));
        } catch (Exception e) {
            log.error("[state-machine] 重试实例失败 id={}", id, e);
            return Map.of("message", "Re-execution failed: " + e.getMessage());
        }
    }

}
