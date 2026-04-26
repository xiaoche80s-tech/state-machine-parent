package cn.chedejun.demo.controller;

import cn.chedejun.demo.dto.OrderCreateRequest;
import cn.chedejun.demo.dto.OrderResumeByIdRequest;
import cn.chedejun.demo.dto.OrderResumeRequest;
import cn.chedejun.demo.dto.OutboundCreateRequest;
import cn.chedejun.demo.dto.OutboundResumeByIdRequest;
import cn.chedejun.demo.dto.OutboundResumeRequest;
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
    public Map<String, Object> createOrder(@RequestBody OrderCreateRequest req) {
        String orderId = UUID.randomUUID().toString().substring(0, 8);
        int stock = req.stock() != 0 ? req.stock() : 10;
        double amount = req.amount() != 0 ? req.amount() : 99.99;
        String address = req.address() != null && !req.address().isBlank() ? req.address() : "北京市朝阳区";

        OrderContext ctx = new OrderContext(orderId, stock, amount);
        ctx.setShippingAddress(address);

        try {
            ExecuteResult result = orderMachine.execute(ctx, orderId);
            return Map.of(
                "success", true,
                "orderId", orderId,
                "instanceId", result.instanceId(),
                "status", result.status(),
                "currentState", result.currentState(),
                "message", "订单执行完成"
            );
        } catch (StateMachineException e) {
            return Map.of(
                "success", false,
                "orderId", orderId,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", stackTrace(e)
            );
        }
    }

    /**
     * 恢复挂起的订单实例
     */
    @PostMapping("/order/resume")
    public Map<String, Object> resumeOrder(@RequestBody OrderResumeRequest req) {
        if (req.businessId() == null || req.businessId().isBlank()) {
            return Map.of("success", false, "message", "缺少 businessId 参数");
        }
        if (req.expectedCurrentState() == null || req.expectedCurrentState().isBlank()) {
            return Map.of("success", false, "message", "缺少 expectedCurrentState 参数");
        }

        try {
            orderMachine.resumeByBusinessId("order-process", req.businessId(), req.expectedCurrentState(), ctx -> {
                if (req.shippingAddress() != null && !req.shippingAddress().isBlank()) {
                    ctx.setShippingAddress(req.shippingAddress());
                }
            });
            return Map.of(
                "success", true,
                "businessId", req.businessId(),
                "message", "订单已恢复执行，请查询 /demo/orders 查看最新状态"
            );
        } catch (StateMachineException e) {
            return Map.of(
                "success", false,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", stackTrace(e)
            );
        }
    }

    /**
     * 通过实例 ID 恢复挂起的订单实例
     */
    @PostMapping("/order/resume/{instanceId}")
    public Map<String, Object> resumeOrderById(@PathVariable String instanceId,
                                               @RequestBody OrderResumeByIdRequest req) {
        if (req.expectedCurrentState() == null || req.expectedCurrentState().isBlank()) {
            return Map.of("success", false, "message", "缺少 expectedCurrentState 参数");
        }

        try {
            orderMachine.resumeByInstanceId(instanceId, req.expectedCurrentState(), ctx -> {
                if (req.shippingAddress() != null && !req.shippingAddress().isBlank()) {
                    ctx.setShippingAddress(req.shippingAddress());
                }
            });
            return Map.of(
                "success", true,
                "instanceId", instanceId,
                "message", "订单已恢复执行"
            );
        } catch (StateMachineException e) {
            return Map.of(
                "success", false,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", stackTrace(e)
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
                "businessId", r.businessId() != null ? r.businessId() : "",
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
            "instance", Map.of("id", instance.get().id(), "status", instance.get().status(),
                "businessId", instance.get().businessId() != null ? instance.get().businessId() : ""),
            "snapshots", snaps
        );
    }

    /**
     * 重试失败订单
     */
    @PostMapping("/orders/{id}/retry")
    public Map<String, String> retryOrder(@PathVariable String id) {
        orderMachine.retry(id);
        var updated = instanceRepository.findById(id).orElse(null);
        return Map.of("message", "已重新执行，当前状态: " + (updated != null ? updated.status() : "unknown"));
    }

    // ===== 出库流程 =====

    /**
     * 触发出库流程
     */
    @PostMapping("/outbound")
    public Map<String, Object> createOutbound(@RequestBody OutboundCreateRequest req) {
        String outboundNo = "OB-" + UUID.randomUUID().toString().substring(0, 8);
        String warehouseCode = req.warehouseCode() != null && !req.warehouseCode().isBlank() ? req.warehouseCode() : "WH01";
        int totalQty = req.totalQty() != 0 ? req.totalQty() : 100;
        String carrierCode = req.carrierCode() != null && !req.carrierCode().isBlank() ? req.carrierCode() : "SF";

        OutboundContext ctx = new OutboundContext(outboundNo, warehouseCode, totalQty);
        ctx.setCarrierCode(carrierCode);

        try {
            ExecuteResult result = outboundMachine.execute(ctx, outboundNo);
            return Map.of(
                "success", true,
                "outboundNo", outboundNo,
                "instanceId", result.instanceId(),
                "status", result.status(),
                "currentState", result.currentState(),
                "message", result.status()
            );
        } catch (StateMachineException e) {
            return Map.of(
                "success", false,
                "outboundNo", outboundNo,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", stackTrace(e)
            );
        }
    }

    /**
     * 恢复挂起的出库实例
     */
    @PostMapping("/outbound/resume")
    public Map<String, Object> resumeOutbound(@RequestBody OutboundResumeRequest req) {
        if (req.businessId() == null || req.businessId().isBlank()) {
            return Map.of("success", false, "message", "缺少 businessId 参数");
        }
        if (req.expectedCurrentState() == null || req.expectedCurrentState().isBlank()) {
            return Map.of("success", false, "message", "缺少 expectedCurrentState 参数");
        }

        try {
            outboundMachine.resumeByBusinessId("outbound-process", req.businessId(), req.expectedCurrentState(), ctx -> {
                if (req.carrierCode() != null && !req.carrierCode().isBlank()) {
                    ctx.setCarrierCode(req.carrierCode());
                }
            });
            return Map.of(
                "success", true,
                "businessId", req.businessId(),
                "message", "出库已恢复执行，请查询 /demo/outbounds 查看最新状态"
            );
        } catch (StateMachineException e) {
            return Map.of(
                "success", false,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", stackTrace(e)
            );
        }
    }

    /**
     * 通过实例 ID 恢复挂起的出库实例
     */
    @PostMapping("/outbound/resume/{instanceId}")
    public Map<String, Object> resumeOutboundById(@PathVariable String instanceId,
                                                  @RequestBody OutboundResumeByIdRequest req) {
        if (req.expectedCurrentState() == null || req.expectedCurrentState().isBlank()) {
            return Map.of("success", false, "message", "缺少 expectedCurrentState 参数");
        }

        try {
            outboundMachine.resumeByInstanceId(instanceId, req.expectedCurrentState(), ctx -> {
                if (req.carrierCode() != null && !req.carrierCode().isBlank()) {
                    ctx.setCarrierCode(req.carrierCode());
                }
            });
            return Map.of(
                "success", true,
                "instanceId", instanceId,
                "message", "出库已恢复执行"
            );
        } catch (StateMachineException e) {
            return Map.of(
                "success", false,
                "status", "FAILED",
                "message", e.getMessage(),
                "stackTrace", stackTrace(e)
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
                "businessId", r.businessId() != null ? r.businessId() : "",
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
            "instance", Map.of("id", instance.get().id(), "status", instance.get().status(),
                "businessId", instance.get().businessId() != null ? instance.get().businessId() : ""),
            "snapshots", snaps
        );
    }

    /**
     * 重试失败出库单
     */
    @PostMapping("/outbounds/{id}/retry")
    public Map<String, String> retryOutbound(@PathVariable String id) {
        outboundMachine.retry(id);
        var updated = instanceRepository.findById(id).orElse(null);
        return Map.of("message", "已重新执行，当前状态: " + (updated != null ? updated.status() : "unknown"));
    }

    private String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
