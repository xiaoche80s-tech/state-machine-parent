package cn.chedejun.demo.controller;

import cn.chedejun.demo.statemachine.OrderContext;
import cn.chedejun.demo.statemachine.OutboundContext;
import cn.chedejun.statemachine.core.ExecuteResult;
import cn.chedejun.statemachine.core.StateMachine;
import cn.chedejun.statemachine.core.StateMachineException;
import cn.chedejun.statemachine.persistence.InstanceRepository;
import cn.chedejun.statemachine.persistence.SnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final StateMachine<OrderContext> orderMachine;
    private final StateMachine<OutboundContext> outboundMachine;
    private final InstanceRepository instanceRepository;
    private final SnapshotRepository snapshotRepository;

    public DemoController(StateMachine<OrderContext> orderMachine,
                          StateMachine<OutboundContext> outboundMachine,
                          JdbcTemplate jdbcTemplate) {
        this.orderMachine = orderMachine;
        this.outboundMachine = outboundMachine;
        this.instanceRepository = new InstanceRepository(jdbcTemplate);
        this.snapshotRepository = new SnapshotRepository(jdbcTemplate);
    }

    /**
     * 触发订单流程
     */
    @PostMapping("/order")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> params) {
        String orderId = UUID.randomUUID().toString().substring(0, 8);
        int stock = (int) params.getOrDefault("stock", 10);
        double amount = ((Number) params.getOrDefault("amount", 99.99)).doubleValue();
        String address = (String) params.getOrDefault("address", "北京市朝阳区");

        OrderContext ctx = new OrderContext(orderId, stock, amount);
        ctx.setShippingAddress(address);

        try {
            ExecuteResult result = orderMachine.execute(ctx);
            return Map.of(
                "success", true,
                "orderId", orderId,
                "instanceId", result.instanceId(),
                "status", result.status(),
                "currentState", result.currentState(),
                "message", "订单执行完成"
            );
        } catch (StateMachineException e) {
            var instances = instanceRepository.findByMachineName("order-process", 0, 1);
            String instanceId = instances.isEmpty() ? "unknown" : instances.get(0).id();
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return Map.of(
                "success", false,
                "orderId", orderId,
                "instanceId", instanceId,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", sw.toString()
            );
        }
    }

    /**
     * 恢复挂起的订单实例
     * 用于演示 suspendState 用法：当状态机在挂起点暂停后，可通过此接口恢复
     */
    @PostMapping("/order/resume")
    public Map<String, Object> resumeOrder(@RequestBody Map<String, Object> params) {
        String businessId = (String) params.get("businessId");
        String shippingAddress = (String) params.get("shippingAddress");

        if (businessId == null || businessId.isBlank()) {
            return Map.of("success", false, "message", "缺少 businessId 参数");
        }

        try {
            orderMachine.resumeByBusinessId(businessId, ctx -> {
                if (shippingAddress != null && !shippingAddress.isBlank()) {
                    ctx.setShippingAddress(shippingAddress);
                }
            });
            // resumeByBusinessId 内部会更新实例状态，返回成功即可
            return Map.of(
                "success", true,
                "businessId", businessId,
                "message", "订单已恢复执行，请查询 /demo/orders 查看最新状态"
            );
        } catch (StateMachineException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return Map.of(
                "success", false,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", sw.toString()
            );
        }
    }

    /**
     * 查询最近订单
     */
    @GetMapping("/orders")
    public List<Map<String, Object>> listOrders(@RequestParam(defaultValue = "10") int limit) {
        return instanceRepository.findByMachineName("order-process", 0, limit).stream()
            .map(r -> Map.<String, Object>of(
                "id", r.id(),
                "status", r.status(),
                "currentState", r.currentState(),
                "retryCount", r.retryCount(),
                "errorMessage", r.errorMessage() != null ? r.errorMessage() : "",
                "createdAt", r.createdAt().toString()
            )).toList();
    }

    /**
     * 订单执行详情
     */
    @GetMapping("/orders/{id}")
    public Map<String, Object> orderDetail(@PathVariable String id) {
        var instance = instanceRepository.findById(id);
        if (instance.isEmpty()) return Map.of("error", "Instance not found");

        var snaps = snapshotRepository.findByInstanceId(id).stream()
            .map(s -> Map.<String, Object>of(
                "stateName", s.stateName(),
                "status", s.status(),
                "attempt", s.attempt(),
                "executedAt", s.executedAt().toString(),
                "errorMessage", s.errorMessage() != null ? s.errorMessage() : ""
            )).toList();

        return Map.of(
            "instance", Map.of("id", instance.get().id(), "status", instance.get().status()),
            "snapshots", snaps
        );
    }

    /**
     * 重置失败订单并重试
     */
    @PostMapping("/orders/{id}/retry")
    public Map<String, String> retryOrder(@PathVariable String id) {
        instanceRepository.updateState(id, "check-inventory", "RUNNING", null);
        instanceRepository.setRetryCount(id, 0);
        return Map.of("message", "已重置为 RUNNING");
    }

    // ===== 出库流程 =====

    /**
     * 触发出库流程
     */
    @PostMapping("/outbound")
    public Map<String, Object> createOutbound(@RequestBody Map<String, Object> params) {
        String outboundNo = "OB-" + UUID.randomUUID().toString().substring(0, 8);
        String warehouseCode = (String) params.getOrDefault("warehouseCode", "WH01");
        int totalQty = (int) params.getOrDefault("totalQty", 100);
        String carrierCode = (String) params.getOrDefault("carrierCode", "SF");

        OutboundContext ctx = new OutboundContext(outboundNo, warehouseCode, totalQty);
        ctx.setCarrierCode(carrierCode);

        try {
            ExecuteResult result = outboundMachine.execute(ctx);
            return Map.of(
                "success", true,
                "outboundNo", outboundNo,
                "instanceId", result.instanceId(),
                "status", result.status(),
                "currentState", result.currentState(),
                "message", "REACHED".equals(result.status()) ? "停在 " + result.currentState() + "，后续还有状态" : "出库执行完成"
            );
        } catch (StateMachineException e) {
            var instances = instanceRepository.findByMachineName("outbound-process", 0, 1);
            String instanceId = instances.isEmpty() ? "unknown" : instances.get(0).id();
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return Map.of(
                "success", false,
                "outboundNo", outboundNo,
                "instanceId", instanceId,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", sw.toString()
            );
        }
    }

    /**
     * 恢复挂起的出库实例
     * 用于演示 suspendState 用法：当状态机在挂起点暂停后，可通过此接口恢复
     */
    @PostMapping("/outbound/resume")
    public Map<String, Object> resumeOutbound(@RequestBody Map<String, Object> params) {
        String businessId = (String) params.get("businessId");
        String carrierCode = (String) params.get("carrierCode");

        if (businessId == null || businessId.isBlank()) {
            return Map.of("success", false, "message", "缺少 businessId 参数");
        }

        try {
            outboundMachine.resumeByBusinessId(businessId, ctx -> {
                if (carrierCode != null && !carrierCode.isBlank()) {
                    ctx.setCarrierCode(carrierCode);
                }
            });
            // resumeByBusinessId 内部会更新实例状态，返回成功即可
            return Map.of(
                "success", true,
                "businessId", businessId,
                "message", "出库已恢复执行，请查询 /demo/outbounds 查看最新状态"
            );
        } catch (StateMachineException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return Map.of(
                "success", false,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", sw.toString()
            );
        }
    }

    /**
     * 查询最近出库单
     */
    @GetMapping("/outbounds")
    public List<Map<String, Object>> listOutbounds(@RequestParam(defaultValue = "10") int limit) {
        return instanceRepository.findByMachineName("outbound-process", 0, limit).stream()
            .map(r -> Map.<String, Object>of(
                "id", r.id(),
                "status", r.status(),
                "currentState", r.currentState(),
                "retryCount", r.retryCount(),
                "errorMessage", r.errorMessage() != null ? r.errorMessage() : "",
                "createdAt", r.createdAt().toString()
            )).toList();
    }

    /**
     * 出库执行详情
     */
    @GetMapping("/outbounds/{id}")
    public Map<String, Object> outboundDetail(@PathVariable String id) {
        var instance = instanceRepository.findById(id);
        if (instance.isEmpty()) return Map.of("error", "Instance not found");

        var snaps = snapshotRepository.findByInstanceId(id).stream()
            .map(s -> Map.<String, Object>of(
                "stateName", s.stateName(),
                "status", s.status(),
                "attempt", s.attempt(),
                "executedAt", s.executedAt().toString(),
                "errorMessage", s.errorMessage() != null ? s.errorMessage() : ""
            )).toList();

        return Map.of(
            "instance", Map.of("id", instance.get().id(), "status", instance.get().status()),
            "snapshots", snaps
        );
    }
}
