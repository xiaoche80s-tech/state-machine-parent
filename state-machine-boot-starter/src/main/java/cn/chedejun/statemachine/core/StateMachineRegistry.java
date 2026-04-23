package cn.chedejun.statemachine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StateMachineRegistry {
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
        if (definitionRepository.findByNameAndVersion(name, version).isPresent()) {
            machines.put(machineKey(name, version), machine);
            return;
        }

        List<Map<String, String>> stateEntries = machine.getStates().stream()
            .map(s -> Map.of("name", s.getName(), "actionClass", s.getAction().getClass().getName())).toList();
        List<Map<String, Object>> transitionEntries = machine.getTransitions().stream()
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("from", t.getFrom()); m.put("to", t.getTo());
                m.put("conditionExpression", t.getCondition() != null ? t.getCondition().getClass().getName() : null);
                return m;
            }).toList();
        String retryPolicyJson;
        try {
            retryPolicyJson = objectMapper.writeValueAsString(Map.of(
                "maxAttempts", machine.getRetryPolicy().getMaxAttempts(),
                "initialDelayMs", machine.getRetryPolicy().getInitialDelayMs(),
                "maxDelayMs", machine.getRetryPolicy().getMaxDelayMs(),
                "backoffFactor", machine.getRetryPolicy().getBackoffFactor()));
        } catch (Exception e) { retryPolicyJson = "{}"; }

        definitionRepository.save(name, version, stateEntries, transitionEntries, retryPolicyJson);
        machines.put(machineKey(name, version), machine);
    }

    @SuppressWarnings("unchecked")
    public <C> Optional<StateMachine<C>> getLatest(String name) {
        return machines.entrySet().stream().filter(e -> e.getKey().startsWith(name + ":"))
            .max(Map.Entry.comparingByKey()).map(e -> (StateMachine<C>) e.getValue());
    }

    public Set<String> getMachineNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String key : machines.keySet()) names.add(key.split(":")[0]);
        return names;
    }

    public List<DefinitionRepository.DefinitionRecord> getVersions(String name) {
        return definitionRepository.findAllByName(name);
    }

    public List<DefinitionRepository.DefinitionRecord> getAllDefinitions() {
        return definitionRepository.findAll();
    }

    private String machineKey(String name, String version) { return name + ":" + version; }
}
