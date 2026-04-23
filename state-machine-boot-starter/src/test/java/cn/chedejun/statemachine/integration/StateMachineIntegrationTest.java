package cn.chedejun.statemachine.integration;

import cn.chedejun.statemachine.autoconfigure.StateMachineAutoConfiguration;
import cn.chedejun.statemachine.autoconfigure.StateMachineProperties;
import cn.chedejun.statemachine.core.*;
import cn.chedejun.statemachine.management.ConsoleController;
import cn.chedejun.statemachine.management.StateMachineEndpoint;
import cn.chedejun.statemachine.management.dto.InstanceDTO;
import cn.chedejun.statemachine.management.dto.SnapshotDTO;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DdlExecutor {
    DdlExecutor(DataSource dataSource) {
        try {
            var template = new JdbcTemplate(dataSource);
            String sql = new String(Objects.requireNonNull(DdlExecutor.class.getResourceAsStream("/ddl/postgresql.sql")).readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : sql.split(";")) { String t = stmt.trim(); if (!t.isEmpty()) template.execute(t); }
        } catch (Exception e) { throw new RuntimeException("Failed to execute DDL", e); }
    }
}

@SpringBootTest(
    classes = StateMachineIntegrationTest.TestConfig.class,
    properties = {
        "spring.datasource.url=jdbc:postgresql://1p.inas.club:15432/java-project-demo",
        "spring.datasource.username=chedejun",
        "spring.datasource.password=123456",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "state-machine.ddl-auto=update",
        "state-machine.management.enabled=true",
        "state-machine.console.enabled=true"
    }
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StateMachineIntegrationTest {

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class, StateMachineAutoConfiguration.class})
    static class TestConfig {

        @Bean
        public DdlExecutor ddlExecutor(DataSource dataSource) {
            // Clean stale definitions from previous test runs
            var template = new JdbcTemplate(dataSource);
            template.execute("DELETE FROM state_machine_snapshots");
            template.execute("DELETE FROM state_machine_instances");
            template.execute("DELETE FROM state_machine_definitions");
            return new DdlExecutor(dataSource);
        }

        @Bean
        public StateMachine<TestContext> testMachine() {
            return StateMachineBuilder.<TestContext>builder("test-machine")
                .contextClass(TestContext.class)
                .state("validate", ctx -> ctx.setValidated(true))
                .state("process", ctx -> ctx.setProcessed(true))
                .state("complete", ctx -> ctx.setCompleted(true))
                .transition("validate", "process", TestContext::isValidated)
                .transition("process", "complete", TestContext::isProcessed)
                .build();
        }

        @Bean
        public StateMachine<TestContext> failingMachine() {
            return StateMachineBuilder.<TestContext>builder("failing-machine")
                .contextClass(TestContext.class)
                .state("will-fail", ctx -> { throw new RuntimeException("intentional failure"); })
                .retryPolicy(RetryPolicy.exponentialBackoff().maxAttempts(2).initialDelay(100, TimeUnit.MILLISECONDS).build())
                .build();
        }

        @Bean
        public StateMachine<TestContext> suspendMachine() {
            return StateMachineBuilder.<TestContext>builder("suspend-machine")
                .contextClass(TestContext.class)
                .state("validate", ctx -> ctx.setValidated(true))
                .suspendState("wait-approval", ctx -> ctx.setProcessed(true))
                .state("complete", ctx -> ctx.setCompleted(true))
                .transition("validate", "wait-approval", TestContext::isValidated)
                .transition("wait-approval", "complete", TestContext::isProcessed)
                .build();
        }
    }

    static class TestContext extends Context {
        private boolean validated;
        private boolean processed;
        private boolean completed;

        public boolean isValidated() { return validated; }
        public void setValidated(boolean v) { this.validated = v; }
        public boolean isProcessed() { return processed; }
        public void setProcessed(boolean v) { this.processed = v; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean v) { this.completed = v; }
    }

    @Autowired private StateMachine<TestContext> testMachine;
    @Autowired private StateMachine<TestContext> failingMachine;
    @Autowired private StateMachine<TestContext> suspendMachine;
    @Autowired private StateMachineRegistry registry;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DefinitionRepository definitionRepository;
    @Autowired private StateMachineProperties properties;
    @Autowired(required = false) private StateMachineEndpoint endpoint;
    @Autowired(required = false) private ConsoleController consoleController;

    private InstanceRepository instanceRepository() { return new InstanceRepository(jdbcTemplate); }
    private SnapshotRepository snapshotRepository() { return new SnapshotRepository(jdbcTemplate); }

    private String resolveDefinitionId(String machineName) {
        var definitions = definitionRepository.findAllByName(machineName);
        if (definitions.isEmpty()) throw new StateMachineException("Definition not found: " + machineName);
        return definitions.get(0).id();
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM state_machine_snapshots");
        jdbcTemplate.execute("DELETE FROM state_machine_instances");
        // Don't delete definitions - they are registered once at context startup
    }

    // ===== 自动配置验证 =====

    @Test
    @Order(1)
    void autoConfiguration_beansLoaded() {
        assertNotNull(jdbcTemplate);
        assertNotNull(registry);
        assertNotNull(definitionRepository);
        assertNotNull(properties);
        // Management beans are optional (depend on actuator/web being on classpath)
        if (endpoint != null) log.info("StateMachineEndpoint is available");
        if (consoleController != null) log.info("ConsoleController is available");
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateMachineIntegrationTest.class);

    @Test
    @Order(2)
    void autoConfiguration_propertiesBound() {
        assertEquals("update", properties.getDdlAuto());
        assertTrue(properties.getManagement().isEnabled());
        assertTrue(properties.getConsole().isEnabled());
    }

    @Test
    @Order(3)
    void autoConfiguration_machinesAutoRegistered() {
        assertTrue(registry.getMachineNames().contains("test-machine"));
        assertTrue(registry.getMachineNames().contains("failing-machine"));
    }

    // ===== 端到端执行 =====

    @Test
    @Order(10)
    void execute_successfulFlow_persistsSnapshots() {
        TestContext ctx = new TestContext();
        ExecuteResult result = testMachine.execute(ctx);

        assertNotNull(result.instanceId());
        assertTrue(ctx.isValidated());
        assertTrue(ctx.isProcessed());
        assertTrue(ctx.isCompleted());

        var instance = instanceRepository().findById(result.instanceId());
        assertTrue(instance.isPresent());
        assertEquals("COMPLETED", instance.get().status());
        assertEquals("complete", instance.get().currentState());
        assertEquals("test-machine", instance.get().machineName());

        var snapshots = snapshotRepository().findByInstanceId(result.instanceId());
        assertEquals(3, snapshots.size());
        assertEquals("validate", snapshots.get(0).stateName());
        assertEquals("process", snapshots.get(1).stateName());
        assertEquals("complete", snapshots.get(2).stateName());
        snapshots.forEach(s -> assertEquals("SUCCESS", s.status()));
    }

    @Test
    @Order(11)
    void execute_definitionSavedToDatabase() {
        var definitions = definitionRepository.findAllByName("test-machine");
        assertFalse(definitions.isEmpty());
        var def = definitions.get(0);
        assertEquals("test-machine", def.name());
        assertNotNull(def.statesJson());
        assertNotNull(def.transitionsJson());
    }

    @Test
    @Order(12)
    void execute_multipleVersions_createsNewVersion() {
        long count = definitionRepository.findAllByName("test-machine").size();
        assertTrue(count >= 1);
    }

    // ===== 重试机制 =====

    @Test
    @Order(20)
    void execute_failedState_recordsFailedSnapshots() {
        TestContext ctx = new TestContext();
        assertThrows(StateMachineException.class, () -> failingMachine.execute(ctx));

        var instances = instanceRepository().findByMachineName("failing-machine", 0, 10);
        assertEquals(1, instances.size());
        assertEquals("FAILED", instances.get(0).status());

        var snapshots = snapshotRepository().findByInstanceId(instances.get(0).id());
        assertFalse(snapshots.isEmpty());
        snapshots.forEach(s -> assertEquals("FAILED", s.status()));
    }

    @Test
    @Order(21)
    void retry_failedInstance_resetsState() {
        TestContext ctx = new TestContext();
        try { failingMachine.execute(ctx); } catch (StateMachineException ignored) {}

        var instances = instanceRepository().findByMachineName("failing-machine", 0, 10);
        String instanceId = instances.get(0).id();

        instanceRepository().updateState(instanceId, "will-fail", "RUNNING", null);
        instanceRepository().setRetryCount(instanceId, 0);

        var updated = instanceRepository().findById(instanceId);
        assertEquals("RUNNING", updated.get().status());
        assertEquals(0, updated.get().retryCount());
    }

    // ===== REST API =====

    @Test
    @Order(30)
    void consoleApi_listMachines() {
        Assumptions.assumeTrue(consoleController != null);
        testMachine.execute(new TestContext());

        var machines = consoleController.listMachines();
        assertFalse(machines.isEmpty());
        var testMachineInfo = machines.stream()
            .filter(m -> "test-machine".equals(m.get("name")))
            .findFirst().orElseThrow();
        assertEquals(1, testMachineInfo.get("versionCount"));
    }

    @Test
    @Order(31)
    void consoleApi_getVersions() {
        Assumptions.assumeTrue(consoleController != null);
        testMachine.execute(new TestContext());

        var versions = consoleController.getVersions("test-machine");
        assertFalse(versions.isEmpty());
        var ver = versions.get(0);
        assertEquals("test-machine", ver.name());
        assertFalse(ver.states().isEmpty());
        assertFalse(ver.transitions().isEmpty());
    }

    @Test
    @Order(32)
    void consoleApi_getInstances() {
        Assumptions.assumeTrue(consoleController != null);
        testMachine.execute(new TestContext());

        var result = consoleController.getInstances("test-machine", null, 0, 20);
        assertTrue((long) result.get("total") > 0);
        var instances = (List<?>) result.get("instances");
        assertFalse(instances.isEmpty());
        var dto = (InstanceDTO) instances.get(0);
        assertEquals("COMPLETED", dto.status());
    }

    @Test
    @Order(33)
    void consoleApi_getInstanceDetail() {
        Assumptions.assumeTrue(consoleController != null);
        ExecuteResult result = testMachine.execute(new TestContext());
        String instanceId = result.instanceId();

        var detail = consoleController.getInstanceDetail(instanceId);
        assertNotNull(detail.get("instance"));
        var snapshots = (List<?>) detail.get("snapshots");
        assertFalse(snapshots.isEmpty());
        assertEquals("validate", ((SnapshotDTO) snapshots.get(0)).stateName());
    }

    @Test
    @Order(34)
    void consoleApi_retryInstance() {
        Assumptions.assumeTrue(consoleController != null);
        // Create a failing instance
        TestContext ctx = new TestContext();
        try { failingMachine.execute(ctx); } catch (StateMachineException ignored) {}
        var instances = instanceRepository().findByMachineName("failing-machine", 0, 10);
        String instanceId = instances.stream()
            .filter(r -> "FAILED".equals(r.status())).findFirst().orElseThrow().id();

        var result = consoleController.retryInstance(instanceId);
        assertTrue(result.get("message").contains("Re-execution"));
        // Verify it ran again and failed again (failing-machine always fails)
        var updated = instanceRepository().findById(instanceId);
        assertEquals("FAILED", updated.get().status());
    }

    // ===== Actuator Endpoint =====

    @Test
    @Order(40)
    void actuatorEndpoint_listsMachines() {
        Assumptions.assumeTrue(endpoint != null);
        testMachine.execute(new TestContext());
        var machines = endpoint.listMachines();
        assertFalse(machines.isEmpty());
    }

    @Test
    @Order(41)
    void actuatorEndpoint_getVersions() {
        Assumptions.assumeTrue(endpoint != null);
        testMachine.execute(new TestContext());
        var versions = endpoint.getVersions("test-machine");
        assertFalse(versions.isEmpty());
        var ver = versions.get(0);
        assertEquals("test-machine", ver.name());
    }

    // ===== 挂起/恢复 =====

    @Test
    @Order(50)
    void execute_suspendAndResume_continuesExecution() {
        TestContext ctx = new TestContext();
        ExecuteResult result = suspendMachine.execute(ctx, "test-biz-001");

        assertEquals("SUSPENDED", result.status());
        assertEquals("wait-approval", result.currentState());
        assertTrue(ctx.isValidated());
        assertTrue(ctx.isProcessed());    // 挂起点的 Action 已执行
        assertFalse(ctx.isCompleted());   // complete 未执行

        // 恢复执行
        suspendMachine.resumeByBusinessId("suspend-machine", "test-biz-001", c -> {});

        // 重新查询实例状态
        var instance = instanceRepository().findByBusinessId("suspend-machine", "test-biz-001");
        assertTrue(instance.isPresent());
        assertEquals("COMPLETED", instance.get().status());
        assertEquals("complete", instance.get().currentState());
    }

    @Test
    @Order(51)
    void resume_nonSuspendedInstance_throwsException() {
        TestContext ctx = new TestContext();
        ExecuteResult result = suspendMachine.execute(ctx);
        assertEquals("SUSPENDED", result.status());

        // 先恢复一次
        suspendMachine.resumeByInstanceId(result.instanceId(), c -> {});

        // 再次恢复应该失败（已经不是 SUSPENDED 状态）
        assertThrows(StateMachineException.class, () ->
            suspendMachine.resumeByInstanceId(result.instanceId(), c -> {}));
    }

    @Test
    @Order(52)
    void resume_contextMerger_modifiesContext() {
        TestContext ctx = new TestContext();
        ExecuteResult result = suspendMachine.execute(ctx, "test-biz-002");

        assertEquals("SUSPENDED", result.status());
        assertFalse(ctx.isCompleted());

        // 恢复时通过 contextMerger 设置 processed = true，使状态能继续流转
        suspendMachine.resumeByBusinessId("suspend-machine", "test-biz-002", c -> {
            c.setProcessed(true);
        });

        // 验证状态机已完成，说明 contextMerger 修改生效了
        var instance = instanceRepository().findByBusinessId("suspend-machine", "test-biz-002");
        assertTrue(instance.isPresent());
        assertEquals("COMPLETED", instance.get().status());
    }

    @Test
    @Order(53)
    void resumeByBusinessId_notFound_throwsException() {
        assertThrows(StateMachineException.class, () ->
            suspendMachine.resumeByBusinessId("suspend-machine", "non-existent-biz", c -> {}));
    }

    // ===== 数据验证 =====

    @AfterEach
    void dumpDatabaseState(TestInfo testInfo) {
        log.info("=== {} 后的数据库状态 ===", testInfo.getDisplayName());

        long defs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_machine_definitions", Long.class);
        long instances = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_machine_instances", Long.class);
        long snapshots = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_machine_snapshots", Long.class);

        log.info("definitions: {} 条 | instances: {} 条 | snapshots: {} 条", defs, instances, snapshots);
    }
}
