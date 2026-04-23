package cn.chedejun.statemachine.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefinitionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DefinitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String save(String name, String version, List<?> states, List<?> transitions, String retryPolicyJson) {
        String id = UUID.randomUUID().toString();
        String statesJson;
        String transitionsJson;
        try {
            statesJson = objectMapper.writeValueAsString(states);
            transitionsJson = objectMapper.writeValueAsString(transitions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize state machine definition", e);
        }
        jdbcTemplate.update(
            "INSERT INTO state_machine_definitions (id, name, version, states, transitions, retry_policy) VALUES (?, ?, ?, ?, ?, ?)",
            ps -> {
                int i = 1;
                ps.setString(i++, id);
                ps.setString(i++, name);
                ps.setString(i++, version);
                setJson(ps, i++, statesJson);
                setJson(ps, i++, transitionsJson);
                setJson(ps, i++, retryPolicyJson);
            });
        return id;
    }

    private void setJson(PreparedStatement ps, int idx, String json) throws SQLException {
        ps.setObject(idx, json, Types.OTHER);
    }

    public Optional<DefinitionRecord> findByNameAndVersion(String name, String version) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM state_machine_definitions WHERE name = ? AND version = ?",
                rowMapper(), name, version));
        } catch (Exception e) { return Optional.empty(); }
    }

    public List<DefinitionRecord> findAllByName(String name) {
        return jdbcTemplate.query("SELECT * FROM state_machine_definitions WHERE name = ? ORDER BY registered_at DESC", rowMapper(), name);
    }

    public List<DefinitionRecord> findAll() {
        return jdbcTemplate.query("SELECT * FROM state_machine_definitions ORDER BY name, version DESC", rowMapper());
    }

    private RowMapper<DefinitionRecord> rowMapper() {
        return (rs, rowNum) -> new DefinitionRecord(
            rs.getString("id"), rs.getString("name"), rs.getString("version"),
            rs.getString("states"), rs.getString("transitions"), rs.getString("retry_policy"),
            rs.getTimestamp("registered_at").toInstant());
    }

    public record DefinitionRecord(String id, String name, String version,
            String statesJson, String transitionsJson, String retryPolicyJson, Instant registeredAt) {}
}
