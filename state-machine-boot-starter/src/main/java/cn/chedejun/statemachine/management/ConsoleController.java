package cn.chedejun.statemachine.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.management.dto.InstanceDTO;
import cn.chedejun.statemachine.management.dto.MachineDefinitionDTO;
import cn.chedejun.statemachine.management.dto.SnapshotDTO;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
@RequestMapping("/statemachine")
public class ConsoleController {

    @GetMapping({"", "/"})
    public String index() {
        return "forward:/statemachine/index.html";
    }
    private final StateMachineRegistry registry;
    private final InstanceRepository instanceRepository;
    private final SnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsoleController(StateMachineRegistry registry, JdbcTemplate jdbcTemplate) {
        this.registry = registry;
        this.instanceRepository = new InstanceRepository(jdbcTemplate);
        this.snapshotRepository = new SnapshotRepository(jdbcTemplate);
    }

    @GetMapping("/api/machines") @ResponseBody
    public List<Map<String, Object>> listMachines() {
        return registry.getMachineNames().stream().map(name -> {
            var versions = registry.getVersions(name);
            return Map.<String, Object>of(
                "name", name, "versionCount", versions.size(),
                "runningInstances", instanceRepository.countByMachineNameAndStatus(name, "RUNNING"),
                "failedInstances", instanceRepository.countByMachineNameAndStatus(name, "FAILED"));
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
                return new MachineDefinitionDTO(r.id(), r.name(), r.version(), states, transitions, rp, r.registeredAt());
            } catch (Exception e) {
                return new MachineDefinitionDTO(r.id(), r.name(), r.version(), List.of(), List.of(), Map.of(), r.registeredAt());
            }
        }).toList();
    }

    @GetMapping("/api/machines/{name}/instances") @ResponseBody
    public Map<String, Object> getInstances(@PathVariable String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var records = status != null
            ? instanceRepository.findByMachineNameAndStatus(name, status, page * size, size)
            : instanceRepository.findByMachineName(name, page * size, size);
        long total = status != null
            ? instanceRepository.countByMachineNameAndStatus(name, status)
            : instanceRepository.countByMachineNameAndStatus(name, null);
        var dtos = records.stream().map(r -> new InstanceDTO(r.id(), r.machineName(), r.definitionVersion(),
            r.currentState(), r.status(), r.businessId(), r.retryCount(), r.errorMessage(), r.createdAt(), r.updatedAt())).toList();
        return Map.of("instances", dtos, "total", total, "page", page, "size", size);
    }

    @GetMapping("/api/instances/{id}") @ResponseBody
    public Map<String, Object> getInstanceDetail(@PathVariable String id) {
        var inst = instanceRepository.findById(id);
        if (inst.isEmpty()) return Map.of("error", "Instance not found");
        var snaps = snapshotRepository.findByInstanceId(id).stream()
            .map(s -> new SnapshotDTO(s.id(), s.stateName(), s.inputJson(), s.outputJson(),
                s.status(), s.errorMessage(), s.attempt(), s.executedAt())).toList();
        var dto = new InstanceDTO(inst.get().id(), inst.get().machineName(), inst.get().definitionVersion(),
            inst.get().currentState(), inst.get().status(), inst.get().businessId(), inst.get().retryCount(),
            inst.get().errorMessage(), inst.get().createdAt(), inst.get().updatedAt());
        return Map.of("instance", dto, "snapshots", snaps);
    }

    @PostMapping("/api/instances/{id}/resume") @ResponseBody
    public Map<String, Object> resumeInstance(@PathVariable String id,
            @RequestBody Map<String, String> params) {
        var inst = instanceRepository.findById(id);
        if (inst.isEmpty()) return Map.of("success", false, "error", "Instance not found");
        String expectedState = params.get("expectedCurrentState");
        if (expectedState == null || expectedState.isBlank()) {
            return Map.of("success", false, "error", "Missing expectedCurrentState");
        }
        var machine = registry.getLatest(inst.get().machineName());
        if (machine.isEmpty()) return Map.of("success", false, "error", "State machine not found: " + inst.get().machineName());
        try {
            String contextJson = params.get("contextJson");
            @SuppressWarnings("unchecked")
            cn.chedejun.statemachine.core.StateMachine<Object> m =
                (cn.chedejun.statemachine.core.StateMachine<Object>)
                        machine.get();

            if (contextJson != null && !contextJson.isBlank()) {
                final String cj = contextJson;
                m.resumeByInstanceId(id, expectedState, ctx -> {
                    try {
                        objectMapper.readerForUpdating(ctx).readValue(cj);
                    } catch (Exception e) {
                        throw new RuntimeException("解析 contextJson 失败: " + e.getMessage(), e);
                    }
                });
            } else {
                m.resumeByInstanceId(id, expectedState, c -> {});
            }
            var updated = instanceRepository.findById(id);
            return Map.of("success", true, "message", "已恢复执行", "currentState",
                updated.map(InstanceRepository.InstanceRecord::currentState).orElse("unknown"));
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/api/instances/{id}/retry") @ResponseBody
    public Map<String, String> retryInstance(@PathVariable String id) {
        var inst = instanceRepository.findById(id);
        if (inst.isEmpty()) return Map.of("error", "Instance not found");
        var machine = registry.getLatest(inst.get().machineName());
        if (machine.isEmpty()) return Map.of("error", "State machine not found: " + inst.get().machineName());
        try {
            machine.get().retry(id);
            var updated = instanceRepository.findById(id);
            return Map.of("message", "Instance re-executed from state: " + updated.map(r -> r.currentState()).orElse("unknown"));
        } catch (Exception e) {
            return Map.of("message", "Re-execution failed: " + e.getMessage());
        }
    }
}
