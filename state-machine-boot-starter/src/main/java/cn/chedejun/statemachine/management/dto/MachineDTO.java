package cn.chedejun.statemachine.management.dto;
public record MachineDTO(String name, int versionCount, long runningInstances, long failedInstances) {}
