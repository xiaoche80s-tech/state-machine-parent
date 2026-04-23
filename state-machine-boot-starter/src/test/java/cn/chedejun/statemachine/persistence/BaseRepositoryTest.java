package cn.chedejun.statemachine.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

public abstract class BaseRepositoryTest {
    protected JdbcTemplate jdbcTemplate;
    protected ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false");
        jdbcTemplate = new JdbcTemplate(ds);
        createTablesIfNotExists(ds);
        cleanTables();
    }

    private void createTablesIfNotExists(DataSource ds) {
        JdbcTemplate ddl = new JdbcTemplate(ds);
        try {
            ddl.execute("RUNSCRIPT FROM 'classpath:/ddl/h2.sql'");
        } catch (Exception e) { throw new RuntimeException("Failed to execute DDL", e); }
    }

    private void cleanTables() {
        jdbcTemplate.execute("DELETE FROM state_machine_snapshots");
        jdbcTemplate.execute("DELETE FROM state_machine_instances");
        jdbcTemplate.execute("DELETE FROM state_machine_definitions");
    }
}
