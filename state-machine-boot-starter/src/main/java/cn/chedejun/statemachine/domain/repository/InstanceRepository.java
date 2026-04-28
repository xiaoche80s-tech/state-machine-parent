package cn.chedejun.statemachine.domain.repository;

import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.shared.*;

import java.util.List;
import java.util.Optional;

public interface InstanceRepository {
    Optional<InstanceData> findById(InstanceId id);
    Optional<InstanceData> findByBusinessId(MachineName machineName, BusinessId businessId);
    InstanceData save(InstanceData instance);
    int tryMarkRunningFromSuspended(InstanceId id);
    List<InstanceData> findByMachineName(MachineName name, int offset, int limit);
    long countByMachineNameAndStatus(MachineName name, InstanceStatus status);
    List<InstanceData> findByMachineNameAndStatus(MachineName name, InstanceStatus status, int offset, int limit);
    List<InstanceData> findByMachineNameWithFilters(MachineName name, InstanceStatus status,
                                                     BusinessId businessId, InstanceId instanceId, int offset, int limit);
    long countByMachineNameWithFilters(MachineName name, InstanceStatus status,
                                        BusinessId businessId, InstanceId instanceId);
}
