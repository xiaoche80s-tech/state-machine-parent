package cn.chedejun.statemachine.core;

import java.time.Instant;

/**
 * 状态机执行结果。
 * 包含实例 ID 和最终状态，方便业务系统直接使用。
 *
 * status 取值：
 * - COMPLETED : 流程自然结束，后面无后续状态
 * - REACHED   : 到达指定的 targetState，但后面还有未执行的状态
 * - FAILED    : 执行失败，超过最大重试次数
 */
public record ExecuteResult(
        String instanceId,
        String machineName,
        String definitionVersion,
        String currentState,
        String status,
        String errorMessage,
        Instant createdAt
) {}
