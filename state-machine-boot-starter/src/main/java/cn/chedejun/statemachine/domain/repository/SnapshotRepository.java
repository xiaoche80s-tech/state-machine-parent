package cn.chedejun.statemachine.domain.repository;

import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.SnapshotId;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository {
    List<SnapshotData> findByInstanceId(InstanceId id);
    SnapshotData save(SnapshotData snapshot);
    Optional<SnapshotData> findById(SnapshotId id);
}
