package cn.chedejun.statemachine.management.dto;
import java.time.Instant;
public record SnapshotDTO(String id, String stateName, String input, String output,
        String status, String errorMessage, int attempt, Instant executedAt) {}
