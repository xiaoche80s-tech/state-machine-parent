package cn.chedejun.statemachine.management.dto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
public record MachineDefinitionDTO(String id, String name, String version,
        List<Map<String, Object>> states, List<Map<String, Object>> transitions,
        Map<String, Object> retryPolicy, Instant registeredAt) {}
