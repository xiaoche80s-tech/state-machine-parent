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
import cn.chedejun.statemachine.domain.data.DefinitionData;
import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.management.dto.InstanceDTO;
import cn.chedejun.statemachine.management.dto.MachineDefinitionDTO;
import cn.chedejun.statemachine.management.dto.SnapshotDTO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/statemachine")
public class ConsoleController {
    private static final Logger log = LoggerFactory.getLogger(ConsoleController.class);

    private static final int MAX_PAGE_SIZE = 200;

    @GetMapping({"", "/"})
    public String index() {
        return "forward:/statemachine/index.html";
    }
    private final StateMachineRegistry registry;
    private final InstanceRepository instanceRepository;
    private final SnapshotRepository snapshotRepository;
    private final InstanceExecutionService<Object> executionService;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public ConsoleController(StateMachineRegistry registry,
                              InstanceRepository instanceRepository,
                              SnapshotRepository snapshotRepository,
                              InstanceExecutionService<Object> executionService,
                              ObjectMapper objectMapper) {
        this.registry = registry;
        this.instanceRepository = instanceRepository;
        this.snapshotRepository = snapshotRepository;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/machines") @ResponseBody
    public List<Map<String, Object>> listMachines() {
        return registry.getMachineNames().stream().map(name -> {
            List<DefinitionData> versions = registry.getVersions(name);
            MachineName machineName = MachineName.of(name);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("versionCount", versions.size());
            result.put("runningInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.RUNNING));
            result.put("failedInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.FAILED));
            result.put("suspendedInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.SUSPENDED));
            return Collections.unmodifiableMap(result);
        }).collect(Collectors.toList());
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
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), r.registeredAt());
            }
        }).collect(Collectors.toList());
    }

    @GetMapping("/api/machines/{name}/instances") @ResponseBody
    public Map<String, Object> getInstances(@PathVariable String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessId,
            @RequestParam(required = false) String instanceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 校验 size 上限，防止恐怔性请求拉取全量数据
        size = Math.min(size, MAX_PAGE_SIZE);
        MachineName machineName = MachineName.of(name);
        // 校验 status 参数，非法值返回 400 而非 500
        InstanceStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = InstanceStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "Invalid status value: " + status + ". Valid values: RUNNING, COMPLETED, FAILED, SUSPENDED");
                return Collections.unmodifiableMap(err);
            }
        }
        BusinessId businessIdObj = businessId != null && !businessId.isEmpty() ? BusinessId.of(businessId) : null;
        InstanceId instanceIdObj = instanceId != null && !instanceId.isEmpty() ? InstanceId.of(instanceId) : null;

        boolean hasFilters = statusEnum != null || businessIdObj != null || instanceIdObj != null;
        List<InstanceData> records = hasFilters
            ? instanceRepository.findByMachineNameWithFilters(machineName, statusEnum, businessIdObj, instanceIdObj, page * size, size)
            : instanceRepository.findByMachineName(machineName, page * size, size);
        long total = hasFilters
            ? instanceRepository.countByMachineNameWithFilters(machineName, statusEnum, businessIdObj, instanceIdObj)
            : instanceRepository.countByMachineNameWithFilters(machineName, null, null, null);
        List<InstanceDTO> dtos = records.stream().map(r -> new InstanceDTO(r.id().value(), r.machineName().value(), r.definitionVersion(),
            r.currentState().value(), r.status().name(), r.businessId() != null ? r.businessId().value() : null, r.retryCount(), r.errorMessage(), r.createdAt(), r.updatedAt())).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instances", dtos);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Collections.unmodifiableMap(result);
    }

    @GetMapping("/api/instances/{id}") @ResponseBody
    public Map<String, Object> getInstanceDetail(@PathVariable String id) {
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "Instance not found");
            return Collections.unmodifiableMap(err);
        }
        List<SnapshotDTO> snaps = snapshotRepository.findByInstanceId(InstanceId.of(id)).stream()
            .map(s -> new SnapshotDTO(s.id().value(), s.stateName().value(), s.inputJson(), s.outputJson(),
                s.status().name(), s.errorMessage(), s.attempt(), s.snapshotType(), s.executedAt())).collect(Collectors.toList());
        InstanceData r = inst.get();
        InstanceDTO dto = new InstanceDTO(r.id().value(), r.machineName().value(), r.definitionVersion(),
            r.currentState().value(), r.status().name(), r.businessId() != null ? r.businessId().value() : null, r.retryCount(),
            r.errorMessage(), r.createdAt(), r.updatedAt());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", dto);
        result.put("snapshots", snaps);
        return Collections.unmodifiableMap(result);
    }

    @PostMapping("/api/instances/{id}/resume") @ResponseBody
    public Map<String, Object> resumeInstance(@PathVariable String id,
            @RequestBody Map<String, String> params) {
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Instance not found");
            return Collections.unmodifiableMap(err);
        }
        String expectedState = params.get("expectedCurrentState");
        if (expectedState == null || expectedState.trim().isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Missing expectedCurrentState");
            return Collections.unmodifiableMap(err);
        }
        Optional<? extends StateMachine<?>> machineOpt = registry.getLatest(inst.get().machineName().value());
        if (!machineOpt.isPresent()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "State machine not found: " + inst.get().machineName().value());
            return Collections.unmodifiableMap(err);
        }
        try {
            String contextJson = params.get("contextJson");
            @SuppressWarnings("unchecked")
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();

            if (contextJson != null && !contextJson.trim().isEmpty()) {
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
            Optional<InstanceData> updated = instanceRepository.findById(InstanceId.of(id));
            Map<String, Object> success = new LinkedHashMap<>();
            success.put("success", true);
            success.put("message", "已恢复执行");
            success.put("currentState", updated.map(i -> i.currentState().value()).orElse("unknown"));
            return Collections.unmodifiableMap(success);
        } catch (Exception e) {
            log.error("[state-machine] 恢复实例失败 id={}", id, e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return Collections.unmodifiableMap(err);
        }
    }

    @PostMapping("/api/instances/{id}/retry") @ResponseBody
    public Map<String, String> retryInstance(@PathVariable String id) {
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "Instance not found");
            return Collections.unmodifiableMap(err);
        }
        Optional<? extends StateMachine<?>> machineOpt = registry.getLatest(inst.get().machineName().value());
        if (!machineOpt.isPresent()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "State machine not found: " + inst.get().machineName().value());
            return Collections.unmodifiableMap(err);
        }
        try {
            @SuppressWarnings("unchecked")
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();
            executionService.retry(m, InstanceId.of(id));
            Optional<InstanceData> updated = instanceRepository.findById(InstanceId.of(id));
            Map<String, String> result = new LinkedHashMap<>();
            result.put("message", "Instance re-executed from state: " + updated.map(i -> i.currentState().value()).orElse("unknown"));
            return Collections.unmodifiableMap(result);
        } catch (Exception e) {
            log.error("[state-machine] 重试实例失败 id={}", id, e);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("message", "Re-execution failed: " + e.getMessage());
            return Collections.unmodifiableMap(result);
        }
    }

}
