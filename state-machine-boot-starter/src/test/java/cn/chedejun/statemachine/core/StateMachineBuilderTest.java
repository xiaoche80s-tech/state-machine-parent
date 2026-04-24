package cn.chedejun.statemachine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class StateMachineBuilderTest {
    private JdbcTemplate jdbcTemplate;
    private StateMachineRegistry registry;

    @BeforeEach void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:testdb_builder;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false");
        jdbcTemplate = new JdbcTemplate(ds);
        try {
            String sql = new String(Objects.requireNonNull(getClass().getResourceAsStream("/ddl/h2.sql")).readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : sql.split(";")) { String t = stmt.trim(); if (!t.isEmpty()) jdbcTemplate.execute(t); }
        } catch (Exception e) { throw new RuntimeException(e); }
        registry = new StateMachineRegistry(new DefinitionRepository(jdbcTemplate, new ObjectMapper()));
    }

    @Test void build_withoutJdbcTemplate_throwsOnExecute() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("no-db")
            .state("step", ctx -> {}).transition("step", "step", ctx -> true).build();
        assertThrows(StateMachineException.class, () -> m.execute(new Context(), "biz-no-db"));
    }

    @Test void build_withJdbcTemplate_executesSuccessfully() {
        AtomicBoolean executed = new AtomicBoolean(false);
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("with-db")
            .state("step", ctx -> executed.set(true))
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();
        ExecuteResult result = m.execute(new Context(), "biz-builder-test");
        assertTrue(executed.get());
        assertNotNull(result.instanceId());
    }

    @Test void build_withRegistry_registersDefinition() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("registered")
            .state("step", ctx -> {}).transition("step", "step", ctx -> true)
            .retryPolicy(RetryPolicy.none()).jdbcTemplate(jdbcTemplate).registry(registry).build();
        assertTrue(registry.getMachineNames().contains("registered"));
        assertEquals(1, registry.getVersions("registered").size());
    }

    @Test void build_assignsVersion() {
        StateMachine<Context> m1 = StateMachineBuilder.<Context>builder("ver")
            .state("s", ctx -> {}).jdbcTemplate(jdbcTemplate).build();
        StateMachine<Context> m2 = StateMachineBuilder.<Context>builder("ver")
            .state("s", ctx -> {}).jdbcTemplate(jdbcTemplate).build();
        assertNotEquals(m1.getVersion(), m2.getVersion());
    }

    @Test void blankName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> StateMachineBuilder.<Context>builder(""));
    }

    @Test void execute_noNextTransition_returnsCompleted() {
        AtomicBoolean step3Executed = new AtomicBoolean(false);
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("complete-test")
            .state("step1", ctx -> ctx.put("done", true))
            .state("step2", ctx -> {})
            .state("step3", ctx -> step3Executed.set(true))
            .transition("step1", "step2", ctx -> true)
            // step2 没有后续 Transition，流程自然结束
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();

        ExecuteResult result = m.execute(new Context(), "biz-builder-test");

        assertEquals("COMPLETED", result.status());
        assertEquals("step2", result.currentState());
        assertFalse(step3Executed.get()); // step3 不应该被执行
    }

    @Test
    void execute_suspendPoint_stopsExecution() {
        AtomicBoolean step2Executed = new AtomicBoolean(false);
        AtomicBoolean step3Executed = new AtomicBoolean(false);
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("suspend-exec")
            .state("step1", ctx -> ctx.put("step1", true))
            .suspendState("step2", ctx -> step2Executed.set(true))
            .state("step3", ctx -> step3Executed.set(true))
            .transition("step1", "step2", ctx -> true)
            .transition("step2", "step3", ctx -> true)
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();

        ExecuteResult result = m.execute(new Context(), "biz-builder-test");

        assertEquals("SUSPENDED", result.status());
        assertEquals("step2", result.currentState());
        assertTrue(step2Executed.get());  // 挂起点的 Action 已执行
        assertFalse(step3Executed.get()); // step3 未执行
    }

    @Test
    void state_defaultNotSuspended() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("suspended-test")
            .state("normal", ctx -> {})
            .suspendState("suspend-point", ctx -> {})
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();

        State<Context> normal = m.getStates().stream()
            .filter(s -> s.getName().equals("normal")).findFirst().orElseThrow();
        State<Context> suspended = m.getStates().stream()
            .filter(s -> s.getName().equals("suspend-point")).findFirst().orElseThrow();

        assertFalse(normal.isSuspended());
        assertTrue(suspended.isSuspended());
    }
}
