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
        assertThrows(StateMachineException.class, () -> m.execute(new Context()));
    }

    @Test void build_withJdbcTemplate_executesSuccessfully() {
        AtomicBoolean executed = new AtomicBoolean(false);
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("with-db")
            .state("step", ctx -> executed.set(true))
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();
        ExecuteResult result = m.execute(new Context());
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

        ExecuteResult result = m.execute(new Context());

        assertEquals("COMPLETED", result.status());
        assertEquals("step2", result.currentState());
        assertFalse(step3Executed.get()); // step3 不应该被执行
    }

    @Test void execute_withTargetState_returnsReached() {
        AtomicBoolean step3Executed = new AtomicBoolean(false);
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("reached-test")
            .state("step1", ctx -> ctx.put("done", true))
            .state("step2", ctx -> {})
            .state("step3", ctx -> step3Executed.set(true))
            .transition("step1", "step2", ctx -> true)
            .transition("step2", "step3", ctx -> true)
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();

        // 指定 targetState 为 step2，step2 后面还有 step3，应该返回 REACHED
        ExecuteResult result = m.execute(new Context(), "step2");

        assertEquals("REACHED", result.status());
        assertEquals("step2", result.currentState());
        assertFalse(step3Executed.get());
    }

    @Test void execute_targetStateIsLastState_returnsCompleted() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("last-state-test")
            .state("step1", ctx -> {})
            .state("step2", ctx -> {})
            .state("step3", ctx -> ctx.put("done", true))
            .transition("step1", "step2", ctx -> true)
            .transition("step2", "step3", ctx -> true)
            // step3 没有后续 Transition
            .retryPolicy(RetryPolicy.none())
            .jdbcTemplate(jdbcTemplate).build();

        // targetState 是最后一个状态，后面没有后续状态，应该返回 COMPLETED
        ExecuteResult result = m.execute(new Context(), "step3");

        assertEquals("COMPLETED", result.status());
        assertEquals("step3", result.currentState());
    }
}
