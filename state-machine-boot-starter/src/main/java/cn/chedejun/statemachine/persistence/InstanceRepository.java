package cn.chedejun.statemachine.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InstanceRepository {
    private final JdbcTemplate jdbcTemplate;
    public InstanceRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public String create(String definitionId, String machineName, String definitionVersion, String initialState) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO state_machine_instances (id, definition_id, machine_name, definition_version, current_state, status) VALUES (?, ?, ?, ?, ?, 'RUNNING')",
            id, definitionId, machineName, definitionVersion, initialState);
        return id;
    }

    public Optional<InstanceRecord> findById(String id) {
        try { return Optional.ofNullable(jdbcTemplate.queryForObject("SELECT * FROM state_machine_instances WHERE id = ?", rowMapper(), id)); }
        catch (Exception e) { return Optional.empty(); }
    }

    public void updateState(String id, String currentState, String status, String errorMessage) {
        jdbcTemplate.update("UPDATE state_machine_instances SET current_state = ?, status = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", currentState, status, errorMessage, id);
    }

    public void incrementRetry(String id, int retryCount, Instant nextRetryAt) {
        jdbcTemplate.update("UPDATE state_machine_instances SET retry_count = ?, next_retry_at = ?, status = 'RUNNING', updated_at = CURRENT_TIMESTAMP WHERE id = ?", retryCount, Timestamp.from(nextRetryAt), id);
    }

    public void setRetryCount(String id, int retryCount) {
        jdbcTemplate.update("UPDATE state_machine_instances SET retry_count = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", retryCount, id);
    }

    public List<InstanceRecord> findByMachineName(String machineName, int offset, int limit) {
        return jdbcTemplate.query("SELECT * FROM state_machine_instances WHERE machine_name = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper(), machineName, limit, offset);
    }

    public long countByMachineNameAndStatus(String machineName, String status) {
        if (status == null) return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_machine_instances WHERE machine_name = ?", Long.class, machineName);
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_machine_instances WHERE machine_name = ? AND status = ?", Long.class, machineName, status);
    }

    public List<InstanceRecord> findByMachineNameAndStatus(String machineName, String status, int offset, int limit) {
        return jdbcTemplate.query("SELECT * FROM state_machine_instances WHERE machine_name = ? AND status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper(), machineName, status, limit, offset);
    }

    private RowMapper<InstanceRecord> rowMapper() {
        return (rs,  rowNum) -> new InstanceRecord(
            rs.getString("id"), rs.getString("definition_id"), rs.getString("machine_name"),
            rs.getString("definition_version"), rs.getString("current_state"),
            rs.getString("status"), rs.getInt("retry_count"),
            rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toInstant() : null,
            rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    public record InstanceRecord(String id, String definitionId, String machineName, String definitionVersion,
            String currentState, String status, int retryCount,
            Instant nextRetryAt, String errorMessage, Instant createdAt, Instant updatedAt) {}
}
