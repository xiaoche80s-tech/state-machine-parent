package cn.chedejun.statemachine.management.dto;
import java.time.Instant;
public record InstanceDTO(String id, String machineName, String definitionVersion,
        String currentState, String status, String businessId, int retryCount, String errorMessage,
        Instant createdAt, Instant updatedAt) {}
