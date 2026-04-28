package cn.chedejun.statemachine.domain.data;

import cn.chedejun.statemachine.domain.shared.*;

import java.time.Instant;

public record SnapshotData(
    SnapshotId id,
    InstanceId instanceId,
    StateName stateName,
    String inputJson,
    String outputJson,
    ExecutionStatus status,
    String errorMessage,
    int attempt,
    String snapshotType,
    Instant executedAt
) {
}
