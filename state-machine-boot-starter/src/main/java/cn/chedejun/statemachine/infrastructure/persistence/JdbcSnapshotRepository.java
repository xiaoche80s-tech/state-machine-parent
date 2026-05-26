package cn.chedejun.statemachine.infrastructure.persistence;

import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.domain.shared.*;
import cn.chedejun.statemachine.domain.snapshot.ExecutionSnapshot;
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
    private volatile Boolean postgresql;

    public JdbcSnapshotRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<SnapshotData> findByInstanceId(InstanceId id) {
        return jdbcTemplate.query(
            "SELECT * FROM state_machine_snapshots WHERE instance_id=? ORDER BY executed_at ASC, attempt ASC",
            rowMapper(), id.value());
    }

    @Override
    public SnapshotData save(ExecutionSnapshot snapshot) {
        jdbcTemplate.update(
            "INSERT INTO state_machine_snapshots (id, instance_id, state_name, input, output, status, error_message, attempt, snapshot_type, executed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                ps.setTimestamp(i++, java.sql.Timestamp.from(snapshot.executedAt()));
            });
        return toSnapshotData(snapshot);
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
        if (json == null) {
            ps.setNull(idx, isPostgresql(ps) ? Types.OTHER : Types.VARCHAR);
        } else if (isPostgresql(ps)) {
            ps.setObject(idx, json, Types.OTHER);
        } else {
            ps.setString(idx, json);
        }
    }

    private boolean isPostgresql(PreparedStatement ps) throws SQLException {
        if (postgresql == null) {
            synchronized (this) {
                if (postgresql == null) {
                    String dbName = ps.getConnection().getMetaData().getDatabaseProductName();
                    postgresql = dbName != null && dbName.toLowerCase().contains("postgresql");
                }
            }
        }
        return postgresql;
    }

    private SnapshotData toSnapshotData(ExecutionSnapshot snapshot) {
        return new SnapshotData(
            snapshot.id(), snapshot.instanceId(), snapshot.stateName(),
            snapshot.inputJson(), snapshot.outputJson(), snapshot.status(),
            snapshot.errorMessage(), snapshot.attempt(), snapshot.snapshotType(),
            snapshot.executedAt());
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
