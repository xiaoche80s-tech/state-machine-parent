package cn.chedejun.statemachine.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * 状态机执行结果。
 * 包含实例 ID 和最终状态，方便业务系统直接使用。
 *
 * status 取值：
 * - COMPLETED  : 流程自然结束，后面无后续状态
 * - FAILED     : 执行失败，超过最大重试次数
 * - SUSPENDED  : 流程在挂起点暂停，等待外部唤醒
 */
public final class ExecuteResult {
    private final String instanceId;
    private final String machineName;
    private final String definitionVersion;
    private final String currentState;
    private final String status;
    private final String errorMessage;
    private final String businessId;
    private final Instant createdAt;

    public ExecuteResult(String instanceId, String machineName, String definitionVersion,
                         String currentState, String status, String errorMessage,
                         String businessId, Instant createdAt) {
        this.instanceId = instanceId;
        this.machineName = machineName;
        this.definitionVersion = definitionVersion;
        this.currentState = currentState;
        this.status = status;
        this.errorMessage = errorMessage;
        this.businessId = businessId;
        this.createdAt = createdAt;
    }

    @JsonProperty public String instanceId() { return instanceId; }
    @JsonProperty public String machineName() { return machineName; }
    @JsonProperty public String definitionVersion() { return definitionVersion; }
    @JsonProperty public String currentState() { return currentState; }
    @JsonProperty public String status() { return status; }
    @JsonProperty public String errorMessage() { return errorMessage; }
    @JsonProperty public String businessId() { return businessId; }
    @JsonProperty public Instant createdAt() { return createdAt; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecuteResult that = (ExecuteResult) o;
        return Objects.equals(instanceId, that.instanceId);
    }
    @Override public int hashCode() { return Objects.hash(instanceId); }
    @Override public String toString() {
        return "ExecuteResult{instanceId='" + instanceId + "', status='" + status + "'}";
    }
}
