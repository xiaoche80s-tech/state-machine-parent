package cn.chedejun.statemachine.domain.repository;

import cn.chedejun.statemachine.domain.data.DefinitionData;
import cn.chedejun.statemachine.domain.shared.MachineName;

import java.util.List;
import java.util.Optional;

public interface DefinitionRepository {
    void save(DefinitionData definition);
    void update(DefinitionData definition);
    Optional<DefinitionData> findByNameAndVersion(MachineName name, String version);
    List<DefinitionData> findAllByName(MachineName name);
    List<DefinitionData> findAll();
}
