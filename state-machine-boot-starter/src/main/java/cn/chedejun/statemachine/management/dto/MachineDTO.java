package cn.chedejun.statemachine.management.dto;

import java.util.Objects;

public final class MachineDTO {
    private final String name;
    private final int versionCount;
    private final long runningInstances;
    private final long failedInstances;

    public MachineDTO(String name, int versionCount, long runningInstances, long failedInstances) {
        this.name = name;
        this.versionCount = versionCount;
        this.runningInstances = runningInstances;
        this.failedInstances = failedInstances;
    }

    public String name() { return name; }
    public int versionCount() { return versionCount; }
    public long runningInstances() { return runningInstances; }
    public long failedInstances() { return failedInstances; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MachineDTO that = (MachineDTO) o;
        return Objects.equals(name, that.name);
    }
    @Override public int hashCode() { return Objects.hash(name); }
    @Override public String toString() {
        return "MachineDTO{name='" + name + "'}";
    }
}
