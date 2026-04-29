package cn.chedejun.statemachine.domain.data;

import cn.chedejun.statemachine.domain.shared.MachineName;

import java.time.Instant;
import java.util.Objects;

public final class DefinitionData {
    private final String id;
    private final MachineName name;
    private final String version;
    private final String statesJson;
    private final String transitionsJson;
    private final String retryPolicyJson;
    private final Instant registeredAt;

    public DefinitionData(String id, MachineName name, String version, String statesJson,
                           String transitionsJson, String retryPolicyJson, Instant registeredAt) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.statesJson = statesJson;
        this.transitionsJson = transitionsJson;
        this.retryPolicyJson = retryPolicyJson;
        this.registeredAt = registeredAt;
    }

    public String id() { return id; }
    public MachineName name() { return name; }
    public String version() { return version; }
    public String statesJson() { return statesJson; }
    public String transitionsJson() { return transitionsJson; }
    public String retryPolicyJson() { return retryPolicyJson; }
    public Instant registeredAt() { return registeredAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefinitionData that = (DefinitionData) o;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() {
        return "DefinitionData{id='" + id + "', name=" + name + ", version='" + version + "'}";
    }
}
