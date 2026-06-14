package cn.chedejun.statemachine.domain.repository;

import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.shared.*;

import java.util.List;
import java.util.Optional;

public interface InstanceRepository {
    Optional<InstanceData> findById(InstanceId id);
    Optional<InstanceData> findByBusinessId(MachineName machineName, BusinessId businessId);
    InstanceData save(InstanceData instance);
    /**
     * CAS 式原子操作：仅当实例状态为 SUSPENDED 时，同时将 status 改为 RUNNING 并更新 current_state。
     * 返回影响行数：1 表示成功，0 表示实例未处于 SUSPENDED（已被其他请求恢复或状态已变）。
     */
    int tryMarkRunningFromSuspended(InstanceId id, StateName nextState);
    /**
     * CAS 式原子操作：仅当实例状态为 FAILED 时，同时将 status 改为 RUNNING，
     * 重置 retry_count 为 0，清空 error_message。
     * 返回影响行数：1 表示成功，0 表示实例未处于 FAILED。
     */
    int tryMarkRunningFromFailed(InstanceId id);
    List<InstanceData> findByMachineName(MachineName name, int offset, int limit);
    long countByMachineNameAndStatus(MachineName name, InstanceStatus status);
    List<InstanceData> findByMachineNameAndStatus(MachineName name, InstanceStatus status, int offset, int limit);
    List<InstanceData> findByMachineNameWithFilters(MachineName name, InstanceStatus status,
                                                     BusinessId businessId, InstanceId instanceId, int offset, int limit);
    long countByMachineNameWithFilters(MachineName name, InstanceStatus status,
                                        BusinessId businessId, InstanceId instanceId);
}
