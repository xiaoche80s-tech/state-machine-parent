package cn.chedejun.statemachine.infrastructure.persistence;

import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.shared.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcInstanceRepository implements InstanceRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcInstanceRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public JdbcInstanceRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public Optional<InstanceData> findById(InstanceId id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM state_machine_instances WHERE id = ?", rowMapper(), id.value()));
        } catch (Exception e) {
            log.warn("[state-machine] Failed to find instance by id={}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<InstanceData> findByBusinessId(MachineName machineName, BusinessId businessId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM state_machine_instances WHERE machine_name = ? AND business_id = ? ORDER BY created_at DESC LIMIT 1",
                rowMapper(), machineName.value(), businessId.value()));
        } catch (Exception e) {
            log.warn("[state-machine] Failed to find instance by businessId={} machineName={}",
                     businessId.value(), machineName.value(), e);
            return Optional.empty();
        }
    }

    @Override
    public InstanceData save(InstanceData instance) {
        if (findById(instance.id()).isPresent()) {
            jdbcTemplate.update(
                "UPDATE state_machine_instances SET definition_id=?, machine_name=?, definition_version=?, " +
                "current_state=?, business_id=?, status=?, retry_count=?, next_retry_at=?, error_message=?, updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=?",
                ps -> {
                    int i = 1;
                    ps.setString(i++, instance.definitionId().value());
                    ps.setString(i++, instance.machineName().value());
                    ps.setString(i++, instance.definitionVersion());
                    ps.setString(i++, instance.currentState().value());
                    ps.setString(i++, instance.businessId().value());
                    ps.setString(i++, instance.status().name());
                    ps.setInt(i++, instance.retryCount());
                    if (instance.nextRetryAt() != null) ps.setTimestamp(i++, Timestamp.from(instance.nextRetryAt()));
                    else ps.setNull(i++, java.sql.Types.TIMESTAMP);
                    ps.setString(i++, instance.errorMessage());
                    ps.setString(i++, instance.id().value());
                });
        } else {
            jdbcTemplate.update(
                "INSERT INTO state_machine_instances (id, definition_id, machine_name, definition_version, " +
                "current_state, business_id, status, retry_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    int i = 1;
                    ps.setString(i++, instance.id().value());
                    ps.setString(i++, instance.definitionId().value());
                    ps.setString(i++, instance.machineName().value());
                    ps.setString(i++, instance.definitionVersion());
                    ps.setString(i++, instance.currentState().value());
                    ps.setString(i++, instance.businessId().value());
                    ps.setString(i++, instance.status().name());
                    ps.setInt(i++, instance.retryCount());
                    ps.setTimestamp(i++, Timestamp.from(instance.createdAt()));
                    ps.setTimestamp(i++, Timestamp.from(instance.updatedAt()));
                });
        }
        return instance;
    }

    @Override
    public int tryMarkRunningFromSuspended(InstanceId id) {
        return jdbcTemplate.update(
            "UPDATE state_machine_instances SET status='RUNNING', updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='SUSPENDED'",
            id.value());
    }

    @Override
    public List<InstanceData> findByMachineName(MachineName name, int offset, int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM state_machine_instances WHERE machine_name=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper(), name.value(), limit, offset);
    }

    @Override
    public long countByMachineNameAndStatus(MachineName name, InstanceStatus status) {
        if (status == null)
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM state_machine_instances WHERE machine_name=?", Long.class, name.value());
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM state_machine_instances WHERE machine_name=? AND status=?",
            Long.class, name.value(), status.name());
    }

    @Override
    public List<InstanceData> findByMachineNameAndStatus(MachineName name, InstanceStatus status, int offset, int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM state_machine_instances WHERE machine_name=? AND status=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper(), name.value(), status.name(), limit, offset);
    }

    @Override
    public List<InstanceData> findByMachineNameWithFilters(MachineName name, InstanceStatus status,
                                                            BusinessId businessId, InstanceId instanceId,
                                                            int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM state_machine_instances WHERE machine_name=?");
        List<Object> params = new ArrayList<>();
        params.add(name.value());
        if (status != null) { sql.append(" AND status=?"); params.add(status.name()); }
        if (businessId != null && businessId.value() != null) { sql.append(" AND business_id=?"); params.add(businessId.value()); }
        if (instanceId != null) { sql.append(" AND id=?"); params.add(instanceId.value()); }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), rowMapper(), params.toArray());
    }

    @Override
    public long countByMachineNameWithFilters(MachineName name, InstanceStatus status,
                                               BusinessId businessId, InstanceId instanceId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM state_machine_instances WHERE machine_name=?");
        List<Object> params = new ArrayList<>();
        params.add(name.value());
        if (status != null) { sql.append(" AND status=?"); params.add(status.name()); }
        if (businessId != null && businessId.value() != null) { sql.append(" AND business_id=?"); params.add(businessId.value()); }
        if (instanceId != null) { sql.append(" AND id=?"); params.add(instanceId.value()); }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private RowMapper<InstanceData> rowMapper() {
        return (rs, rowNum) -> new InstanceData(
            InstanceId.of(rs.getString("id")),
            DefinitionId.of(rs.getString("definition_id")),
            MachineName.of(rs.getString("machine_name")),
            rs.getString("definition_version"),
            StateName.of(rs.getString("current_state")),
            BusinessId.ofNullable(rs.getString("business_id")),
            InstanceStatus.valueOf(rs.getString("status")),
            rs.getInt("retry_count"),
            rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toInstant() : null,
            rs.getString("error_message"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }
}
