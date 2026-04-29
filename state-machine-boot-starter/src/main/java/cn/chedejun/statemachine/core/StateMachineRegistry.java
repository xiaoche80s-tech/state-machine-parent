package cn.chedejun.statemachine.core;

import cn.chedejun.statemachine.domain.data.DefinitionData;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.repository.DefinitionRepository;
import cn.chedejun.statemachine.domain.shared.MachineName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StateMachineRegistry {
    private static final Logger log = LoggerFactory.getLogger(StateMachineRegistry.class);
    private final DefinitionRepository definitionRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, StateMachine<?>> machines = new ConcurrentHashMap<>();

    public StateMachineRegistry(DefinitionRepository definitionRepository) {
        this.definitionRepository = definitionRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @SuppressWarnings("unchecked")
    public <C> void register(StateMachine<C> machine) {
        String name = machine.getName();
        String version = machine.getVersion();

        List<Map<String, Object>> stateEntries = machine.getStates().stream()
            .map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", s.getName());
                m.put("actionClass", s.getAction().getClass().getName());
                if (s.isSuspended()) m.put("suspended", true);
                return m;
            }).collect(Collectors.toList());
        List<Map<String, Object>> transitionEntries = machine.getTransitions().stream()
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("from", t.getFrom()); m.put("to", t.getTo());
                m.put("conditionExpression", t.getCondition() != null ? t.getCondition().getClass().getName() : null);
                return m;
            }).collect(Collectors.toList());
        String retryPolicyJson;
        try {
            Map<String, Object> retryPolicy = new LinkedHashMap<>();
            retryPolicy.put("maxAttempts", machine.getRetryPolicy().getMaxAttempts());
            retryPolicy.put("initialDelayMs", machine.getRetryPolicy().getInitialDelayMs());
            retryPolicy.put("maxDelayMs", machine.getRetryPolicy().getMaxDelayMs());
            retryPolicy.put("backoffFactor", machine.getRetryPolicy().getBackoffFactor());
            retryPolicyJson = objectMapper.writeValueAsString(Collections.unmodifiableMap(retryPolicy));
        } catch (Exception e) {
            log.warn("[state-machine] 序列化状态机重试策略失败 {}:{}", name, version, e);
            retryPolicyJson = "{}";
        }

        MachineName mName = MachineName.of(name);
        DefinitionData data = new DefinitionData(UUID.randomUUID().toString(), mName, version,
            serializeJson(stateEntries), serializeJson(transitionEntries), retryPolicyJson, Instant.now());

        if (definitionRepository.findByNameAndVersion(mName, version).isPresent()) {
            definitionRepository.update(data);
        } else {
            definitionRepository.save(data);
        }
        machines.put(machineKey(name, version), machine);
        log.info("[state-machine] 注册状态机 {}:{}", name, version);
    }

    private String serializeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    public <C> Optional<StateMachine<C>> getLatest(String name) {
        return machines.entrySet().stream().filter(e -> e.getKey().startsWith(name + ":"))
            .max((a, b) -> Integer.compare(extractVersion(a.getKey()), extractVersion(b.getKey())))
            .map(e -> (StateMachine<C>) e.getValue());
    }

    private int extractVersion(String key) {
        String[] parts = key.split(":", 2);
        if (parts.length < 2) return 0;
        try {
            String version = parts[1];
            if (version.startsWith("v")) version = version.substring(1);
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Set<String> getMachineNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String key : machines.keySet()) names.add(key.split(":")[0]);
        return names;
    }

    public List<DefinitionData> getVersions(String name) {
        return definitionRepository.findAllByName(MachineName.of(name));
    }

    public List<DefinitionData> getAllDefinitions() {
        return definitionRepository.findAll();
    }

    private String machineKey(String name, String version) { return name + ":" + version; }
}
