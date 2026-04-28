package cn.chedejun.statemachine.infrastructure.persistence;

import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.domain.shared.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public class JdbcSnapshotRepository implements SnapshotRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcSnapshotRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public JdbcSnapshotRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<SnapshotData> findByInstanceId(InstanceId id) {
        return jdbcTemplate.query(
            "SELECT * FROM state_machine_snapshots WHERE instance_id=? ORDER BY executed_at ASC, attempt ASC",
            rowMapper(), id.value());
    }

    @Override
    public SnapshotData save(SnapshotData snapshot) {
        jdbcTemplate.update(
            "INSERT INTO state_machine_snapshots (id, instance_id, state_name, input, output, status, error_message, attempt, snapshot_type) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ps -> {
                int i = 1;
                ps.setString(i++, snapshot.id().value());
                ps.setString(i++, snapshot.instanceId().value());
                ps.setString(i++, snapshot.stateName().value());
                setJson(ps, i++, snapshot.inputJson());
                setJson(ps, i++, snapshot.outputJson());
                ps.setString(i++, snapshot.status().name());
                ps.setString(i++, snapshot.errorMessage());
                ps.setInt(i++, snapshot.attempt());
                ps.setString(i++, snapshot.snapshotType());
            });
        return snapshot;
    }

    @Override
    public Optional<SnapshotData> findById(SnapshotId id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM state_machine_snapshots WHERE id=?", rowMapper(), id.value()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void setJson(PreparedStatement ps, int idx, String json) throws SQLException {
        ps.setObject(idx, json, Types.OTHER);
    }

    private RowMapper<SnapshotData> rowMapper() {
        return (rs, rowNum) -> new SnapshotData(
            SnapshotId.of(rs.getString("id")),
            InstanceId.of(rs.getString("instance_id")),
            StateName.of(rs.getString("state_name")),
            rs.getString("input"),
            rs.getString("output"),
            ExecutionStatus.valueOf(rs.getString("status")),
            rs.getString("error_message"),
            rs.getInt("attempt"),
            rs.getString("snapshot_type"),
            rs.getTimestamp("executed_at").toInstant());
    }
}
