package cn.chedejun.demo;

import cn.chedejun.demo.statemachine.OrderContext;
import cn.chedejun.statemachine.interfaces.StateMachineFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 演示 Condition 返回 false 时状态机的转换行为。
 */
@SpringBootTest(classes = DemoApplication.class)
class ConditionFailureDemoTest {

    @Autowired
    private StateMachineFacade<OrderContext> orderMachine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM state_machine_snapshots");
        jdbcTemplate.execute("DELETE FROM state_machine_instances");
        jdbcTemplate.execute("DELETE FROM state_machine_definitions");
    }

    /**
     * stock <= 0 → condition "check-inventory -> process-payment" 返回 false，
     * 状态机跳过支付，走 "check-inventory -> notify-shortage" 分支，最终 COMPLETED。
     */
    @Test
    void conditionFalse_onInventoryCheck_routesToNotifyShortage() {
        OrderContext ctx = new OrderContext("ORD-ZERO", 0, 99.0);
        cn.chedejun.statemachine.core.ExecuteResult result = orderMachine.execute(ctx, "ORD-ZERO");
        String instanceId = result.instanceId();

        assertNotNull(instanceId);
        // 当前状态应为终态 notify-shortage（无 outgoing transition）
        java.util.Map<String, Object> inst = jdbcTemplate.queryForList(
            "SELECT current_state, status FROM state_machine_instances WHERE id = ?", instanceId).get(0);
        assertEquals("notify-shortage", inst.get("current_state"));
        assertEquals("COMPLETED", inst.get("status"));
    }

    /**
     * stock > 0 且 paymentSuccess = true，因 50% 机率路由失败 + 30% 机率执行失败，
     * 可能走到 await-ship-confirm（SUSPENDED）、order-failed（COMPLETED）或 process-payment（FAILED）。
     */
    @Test
    void allConditionsTrue_randomOutcomes() {
        OrderContext ctx = new OrderContext("ORD-OK", 10, 99.0);
        ctx.setPaymentSuccess(true);
        ctx.setRouteFailed(false);
        ctx.setShippingAddress("北京市朝阳区");

        String instanceId;
        try {
            cn.chedejun.statemachine.core.ExecuteResult result = orderMachine.execute(ctx, "ORD-OK");
            instanceId = result.instanceId();
        } catch (cn.chedejun.statemachine.core.StateMachineException e) {
            // Math.random() < 0.5 时无匹配过渡，记录为 FAILED
            instanceId = null;
        }

        // 从 DB 查询最新一条 order-process 实例
        java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT current_state, status FROM state_machine_instances WHERE machine_name = 'order-process' ORDER BY created_at DESC LIMIT 1");
        assertFalse(rows.isEmpty());
        java.util.Map<String, Object> inst = rows.get(0);
        String currentState = (String) inst.get("current_state");
        String status = (String) inst.get("status");
        // 可能的结果：
        // - await-ship-confirm + SUSPENDED（路由成功，到达挂起点）
        // - order-failed + COMPLETED（50%路由失败 + routeFailed=true）
        // - process-payment + FAILED（action成功但所有transition condition均不匹配 → 路由失败）
        assertTrue(
            ("await-ship-confirm".equals(currentState) && "SUSPENDED".equals(status))
            || ("order-failed".equals(currentState) && "COMPLETED".equals(status))
            || ("process-payment".equals(currentState) && "FAILED".equals(status)),
            "意外结果: state=" + currentState + ", status=" + status
        );
    }

    /**
     * 通过 routeFailed=true 强制路由失败，验证 condition false 路由行为。
     */
    @Test
    void forceRouteFailure_routesToOrderFailed() {
        OrderContext ctx = new OrderContext("ORD-ROUTE-FAIL", 10, 99.0);
        ctx.setPaymentSuccess(true);
        ctx.setRouteFailed(true);
        cn.chedejun.statemachine.core.ExecuteResult result = orderMachine.execute(ctx, "ORD-ROUTE-FAIL");
        String instanceId = result.instanceId();

        assertNotNull(instanceId);
        java.util.Map<String, Object> inst = jdbcTemplate.queryForList(
            "SELECT current_state, status FROM state_machine_instances WHERE id = ?", instanceId).get(0);
        assertEquals("order-failed", inst.get("current_state"));
        assertEquals("COMPLETED", inst.get("status"));
    }
}
