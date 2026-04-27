package cn.chedejun.statemachine.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

public class SnapshotRepository {
    private final JdbcTemplate jdbcTemplate;
    public SnapshotRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public String save(String instanceId, String stateName, String inputJson, String outputJson, String status, String errorMessage, int attempt, String snapshotType) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO state_machine_snapshots (id, instance_id, state_name, input, output, status, error_message, attempt, snapshot_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                int i = 1;
                ps.setString(i++, id);
                ps.setString(i++, instanceId);
                ps.setString(i++, stateName);
                setJson(ps, i++, inputJson);
                setJson(ps, i++, outputJson);
                ps.setString(i++, status);
                ps.setString(i++, errorMessage);
                ps.setInt(i++, attempt);
                ps.setString(i++, snapshotType);
            });
        return id;
    }

    public String saveRoute(String instanceId, String fromState, String contextJson, String toState) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO state_machine_snapshots (id, instance_id, state_name, input, output, status, error_message, attempt, snapshot_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                int i = 1;
                ps.setString(i++, id);
                ps.setString(i++, instanceId);
                ps.setString(i++, fromState);
                setJson(ps, i++, contextJson);
                setJson(ps, i++, "\"" + toState + "\"");
                ps.setString(i++, "SUCCESS");
                ps.setString(i++, null);
                ps.setInt(i++, 0);
                ps.setString(i++, "ROUTE");
            });
        return id;
    }

    public String saveRouteFailed(String instanceId, String fromState, String contextJson, String errorMessage) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO state_machine_snapshots (id, instance_id, state_name, input, output, status, error_message, attempt, snapshot_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                int i = 1;
                ps.setString(i++, id);
                ps.setString(i++, instanceId);
                ps.setString(i++, fromState);
                setJson(ps, i++, contextJson);
                ps.setString(i++, null);
                ps.setString(i++, "FAILED");
                ps.setString(i++, errorMessage);
                ps.setInt(i++, 0);
                ps.setString(i++, "ROUTE");
            });
        return id;
    }

    private void setJson(PreparedStatement ps, int idx, String json) throws SQLException {
        ps.setObject(idx, json, Types.OTHER);
    }

    public List<SnapshotRecord> findByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM state_machine_snapshots WHERE instance_id = ? ORDER BY executed_at ASC, attempt ASC", rowMapper(), instanceId);
    }

    public SnapshotRecord findById(String id) {
        return jdbcTemplate.queryForObject("SELECT * FROM state_machine_snapshots WHERE id = ?", rowMapper(), id);
    }

    private RowMapper<SnapshotRecord> rowMapper() {
        return (rs, rowNum) -> new SnapshotRecord(
            rs.getString("id"), rs.getString("instance_id"), rs.getString("state_name"),
            rs.getString("input"), rs.getString("output"), rs.getString("status"),
            rs.getString("error_message"), rs.getInt("attempt"), rs.getString("snapshot_type"), rs.getTimestamp("executed_at").toInstant());
    }

    public record SnapshotRecord(String id, String instanceId, String stateName,
            String inputJson, String outputJson, String status, String errorMessage, int attempt, String snapshotType, java.time.Instant executedAt) {}
}
