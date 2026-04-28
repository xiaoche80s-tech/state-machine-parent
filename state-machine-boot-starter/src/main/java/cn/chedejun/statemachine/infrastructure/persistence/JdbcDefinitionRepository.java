package cn.chedejun.statemachine.infrastructure.persistence;

import cn.chedejun.statemachine.domain.data.DefinitionData;
import cn.chedejun.statemachine.domain.repository.DefinitionRepository;
import cn.chedejun.statemachine.domain.shared.MachineName;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public class JdbcDefinitionRepository implements DefinitionRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcDefinitionRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDefinitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(DefinitionData definition) {
        jdbcTemplate.update(
            "INSERT INTO state_machine_definitions (id, name, version, states, transitions, retry_policy) VALUES (?, ?, ?, ?, ?, ?)",
            ps -> {
                int i = 1;
                ps.setString(i++, definition.id());
                ps.setString(i++, definition.name().value());
                ps.setString(i++, definition.version());
                setJson(ps, i++, definition.statesJson());
                setJson(ps, i++, definition.transitionsJson());
                setJson(ps, i++, definition.retryPolicyJson());
            });
    }

    @Override
    public void update(DefinitionData definition) {
        jdbcTemplate.update(
            "UPDATE state_machine_definitions SET states=?, transitions=?, retry_policy=?, registered_at=CURRENT_TIMESTAMP WHERE name=? AND version=?",
            ps -> {
                int i = 1;
                setJson(ps, i++, definition.statesJson());
                setJson(ps, i++, definition.transitionsJson());
                setJson(ps, i++, definition.retryPolicyJson());
                ps.setString(i++, definition.name().value());
                ps.setString(i++, definition.version());
            });
    }

    @Override
    public Optional<DefinitionData> findByNameAndVersion(MachineName name, String version) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM state_machine_definitions WHERE name=? AND version=?",
                rowMapper(), name.value(), version));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<DefinitionData> findAllByName(MachineName name) {
        return jdbcTemplate.query(
            "SELECT * FROM state_machine_definitions WHERE name=? ORDER BY registered_at DESC",
            rowMapper(), name.value());
    }

    @Override
    public List<DefinitionData> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM state_machine_definitions ORDER BY name, version DESC",
            rowMapper());
    }

    private void setJson(PreparedStatement ps, int idx, String json) throws SQLException {
        ps.setObject(idx, json, Types.OTHER);
    }

    private RowMapper<DefinitionData> rowMapper() {
        return (rs, rowNum) -> new DefinitionData(
            rs.getString("id"),
            MachineName.of(rs.getString("name")),
            rs.getString("version"),
            rs.getString("states"),
            rs.getString("transitions"),
            rs.getString("retry_policy"),
            rs.getTimestamp("registered_at").toInstant());
    }
}
