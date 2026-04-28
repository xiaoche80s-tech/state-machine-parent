package cn.chedejun.statemachine.domain.data;

import cn.chedejun.statemachine.domain.shared.*;

import java.time.Instant;

public record InstanceData(
    InstanceId id,
    DefinitionId definitionId,
    MachineName machineName,
    String definitionVersion,
    StateName currentState,
    BusinessId businessId,
    InstanceStatus status,
    int retryCount,
    Instant nextRetryAt,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
    public static InstanceData newInstance(InstanceId id, DefinitionId definitionId, MachineName machineName,
                                            String version, StateName initialState, BusinessId businessId) {
        return new InstanceData(id, definitionId, machineName, version, initialState,
            businessId, InstanceStatus.RUNNING, 0, null, null, Instant.now(), Instant.now());
    }

    public InstanceData withUpdatedState(StateName newState, InstanceStatus newStatus, String newError) {
        return new InstanceData(id, definitionId, machineName, definitionVersion, newState,
            businessId, newStatus, retryCount, nextRetryAt, newError, createdAt, Instant.now());
    }

    public InstanceData withIncrementedRetry(int newRetryCount, Instant newNextRetryAt) {
        return new InstanceData(id, definitionId, machineName, definitionVersion, currentState,
            businessId, InstanceStatus.RUNNING, newRetryCount, newNextRetryAt, errorMessage, createdAt, Instant.now());
    }
}
