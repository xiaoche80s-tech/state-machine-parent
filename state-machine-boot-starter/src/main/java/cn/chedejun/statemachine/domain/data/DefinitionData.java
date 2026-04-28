package cn.chedejun.statemachine.domain.data;

import cn.chedejun.statemachine.domain.shared.MachineName;

import java.time.Instant;

public record DefinitionData(
    String id,
    MachineName name,
    String version,
    String statesJson,
    String transitionsJson,
    String retryPolicyJson,
    Instant registeredAt
) {
}
