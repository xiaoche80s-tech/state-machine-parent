package cn.chedejun.statemachine.management.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MachineDefinitionDTO {
    private final String id;
    private final String name;
    private final String version;
    private final List<Map<String, Object>> states;
    private final List<Map<String, Object>> transitions;
    private final Map<String, Object> retryPolicy;
    private final Instant registeredAt;

    public MachineDefinitionDTO(String id, String name, String version,
                                 List<Map<String, Object>> states,
                                 List<Map<String, Object>> transitions,
                                 Map<String, Object> retryPolicy, Instant registeredAt) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.states = states;
        this.transitions = transitions;
        this.retryPolicy = retryPolicy;
        this.registeredAt = registeredAt;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public List<Map<String, Object>> states() { return states; }
    public List<Map<String, Object>> transitions() { return transitions; }
    public Map<String, Object> retryPolicy() { return retryPolicy; }
    public Instant registeredAt() { return registeredAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MachineDefinitionDTO that = (MachineDefinitionDTO) o;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() {
        return "MachineDefinitionDTO{id='" + id + "', name='" + name + "'}";
    }
}
