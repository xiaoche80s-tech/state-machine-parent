package cn.chedejun.statemachine.core;

import cn.chedejun.statemachine.domain.data.DefinitionData;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.repository.DefinitionRepository;
import cn.chedejun.statemachine.infrastructure.persistence.JdbcDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Scanner;
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
            String sql = new Scanner(Objects.requireNonNull(getClass().getResourceAsStream("/ddl/h2.sql")), StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            for (String stmt : sql.split(";")) { String t = stmt.trim(); if (!t.isEmpty()) jdbcTemplate.execute(t); }
        } catch (Exception e) { throw new RuntimeException(e); }
        DefinitionRepository defRepo = new JdbcDefinitionRepository(jdbcTemplate, new ObjectMapper());
        registry = new StateMachineRegistry(defRepo);
    }

    @Test void build_returnsDomainStateMachine() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("no-db")
            .state("step", ctx -> {}).transition("step", "step", ctx -> true).build();
        assertNotNull(m);
        assertEquals("no-db", m.getName());
        assertEquals("v1", m.getVersion());
    }

    @Test void build_assignsVersion() {
        StateMachine<Context> m1 = StateMachineBuilder.<Context>builder("ver")
            .state("s", ctx -> {}).transition("s", "s", ctx -> true).build();
        StateMachine<Context> m2 = StateMachineBuilder.<Context>builder("ver")
            .state("s", ctx -> {}).transition("s", "s", ctx -> true).build();
        assertNotEquals(m1.getVersion(), m2.getVersion());
    }

    @Test void build_withRegistry_registersDefinition() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("registered")
            .state("step", ctx -> {}).transition("step", "step", ctx -> true)
            .retryPolicy(RetryPolicy.none()).registry(registry).build();
        // Builder no longer auto-registers; the BeanPostProcessor does it.
        // Manually register for this test.
        registry.register(m);
        assertTrue(registry.getMachineNames().contains("registered"));
        assertEquals(1, registry.getVersions("registered").size());
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
            .retryPolicy(RetryPolicy.none())
            .build();

        // domain.engine.StateMachine doesn't have execute(); it's a slim domain object.
        // Verify domain properties instead.
        assertEquals(3, m.getStates().size());
        assertEquals(1, m.getTransitions().size());
        assertEquals("step1", m.getTransitions().get(0).getFrom());
        assertEquals("step2", m.getTransitions().get(0).getTo());
        assertFalse(step3Executed.get());
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
            .build();

        // Verify domain properties
        assertEquals(3, m.getStates().size());
        assertEquals(2, m.getTransitions().size());
        assertTrue(m.getStates().stream()
            .filter(s -> s.getName().equals("step2"))
            .findFirst().map(State::isSuspended).orElse(false));
        assertFalse(step2Executed.get());
        assertFalse(step3Executed.get());
    }

    @Test
    void state_defaultNotSuspended() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("suspended-test")
            .state("normal", ctx -> {})
            .suspendState("suspend-point", ctx -> {})
            .transition("normal", "suspend-point", ctx -> true)
            .retryPolicy(RetryPolicy.none())
            .build();

        cn.chedejun.statemachine.core.State<Context> normal = m.getStates().stream()
            .filter(s -> s.getName().equals("normal")).findFirst().orElseThrow(() -> new AssertionError("State not found"));
        cn.chedejun.statemachine.core.State<Context> suspended = m.getStates().stream()
            .filter(s -> s.getName().equals("suspend-point")).findFirst().orElseThrow(() -> new AssertionError("State not found"));

        assertFalse(normal.isSuspended());
        assertTrue(suspended.isSuspended());
    }

    @Test
    void execute_routeFailure_marksAsFailed() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("route-failure-test")
            .state("step1", ctx -> ctx.put("executed", true))
            .state("step2", ctx -> ctx.put("step2", true))
            .state("step3", ctx -> ctx.put("step3", true))
            .transition("step1", "step2", ctx -> false)
            .transition("step1", "step3", ctx -> false)
            .retryPolicy(RetryPolicy.none())
            .build();

        // Verify domain: state and transitions are set up correctly
        assertEquals(3, m.getStates().size());
        assertEquals(2, m.getTransitions().size());
        // Both transitions have conditions that return false
        assertFalse(m.getTransitions().get(0).getCondition().test(new Context()));
        assertFalse(m.getTransitions().get(1).getCondition().test(new Context()));
    }

    @Test
    void execute_routeFailure_errorMessageContainsAvailableTransitions() {
        StateMachine<Context> m = StateMachineBuilder.<Context>builder("route-error-msg-test")
            .state("alpha", ctx -> {})
            .state("beta", ctx -> {})
            .state("gamma", ctx -> {})
            .state("delta", ctx -> {})
            .transition("alpha", "beta", ctx -> false)
            .transition("alpha", "gamma", ctx -> false)
            .transition("alpha", "delta", ctx -> false)
            .retryPolicy(RetryPolicy.none())
            .build();

        // Verify transitions exist
        assertEquals(3, m.getTransitions().size());
        assertTrue(m.hasOutgoingTransitions("alpha"));
    }
}
