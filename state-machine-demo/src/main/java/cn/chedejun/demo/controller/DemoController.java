package cn.chedejun.demo.controller;

import cn.chedejun.demo.dto.OrderCreateRequest;
import cn.chedejun.demo.dto.OrderResumeRequest;
import cn.chedejun.demo.dto.OutboundCreateRequest;
import cn.chedejun.demo.dto.OutboundResumeRequest;
import cn.chedejun.demo.dto.ReceivingCreateRequest;
import cn.chedejun.demo.dto.ReceivingResumeRequest;
import cn.chedejun.demo.statemachine.OrderContext;
import cn.chedejun.demo.statemachine.OutboundContext;
import cn.chedejun.demo.statemachine.ReceivingContext;
import cn.chedejun.statemachine.core.ExecuteResult;
import cn.chedejun.statemachine.core.StateMachineException;
import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.MachineName;
import cn.chedejun.statemachine.interfaces.StateMachineFacade;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/demo")
public class DemoController {
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final StateMachineFacade<OrderContext> orderMachine;
    private final StateMachineFacade<OutboundContext> outboundMachine;
    private final StateMachineFacade<ReceivingContext> receivingMachine;
    private final InstanceRepository instanceRepository;
    private final SnapshotRepository snapshotRepository;

    public DemoController(StateMachineFacade<OrderContext> orderMachine,
                          StateMachineFacade<OutboundContext> outboundMachine,
                          StateMachineFacade<ReceivingContext> receivingMachine,
                          InstanceRepository instanceRepository,
                          SnapshotRepository snapshotRepository) {
        this.orderMachine = orderMachine;
        this.outboundMachine = outboundMachine;
        this.receivingMachine = receivingMachine;
        this.instanceRepository = instanceRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * 触发订单流程
     */
    @PostMapping("/order")
    public Map<String, Object> createOrder(@RequestBody OrderCreateRequest req) {
        String orderId = UUID.randomUUID().toString().substring(0, 8);
        int stock = req.getStock() != 0 ? req.getStock() : 10;
        double amount = req.getAmount() != 0 ? req.getAmount() : 99.99;
        String address = req.getAddress() != null && !req.getAddress().trim().isEmpty() ? req.getAddress() : "北京市朝阳区";

        OrderContext ctx = new OrderContext(orderId, stock, amount);
        ctx.setShippingAddress(address);


        try {
            ExecuteResult result = orderMachine.execute(ctx, orderId);
            log.info("[demo] Order created: orderId={}, instanceId={}, status={}", orderId, result.instanceId(), result.status());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("orderId", orderId);
            resp.put("instanceId", result.instanceId());
            resp.put("status", result.status());
            resp.put("currentState", result.currentState());
            resp.put("message", "订单执行完成");
            return resp;
        } catch (StateMachineException e) {
            log.error("[demo] Order creation failed: orderId={}", orderId, e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("orderId", orderId);
            resp.put("status", "FAILED");
            resp.put("message", e.getMessage());
            resp.put("stackTrace", stackTrace(e));
            return resp;
        }
    }

    /**
     * 恢复挂起的订单实例
     */
    @PostMapping("/order/resume")
    public Map<String, Object> resumeOrder(@RequestBody OrderResumeRequest req) {
        if (req.getBusinessId() == null || req.getBusinessId().trim().isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "缺少 businessId 参数");
            return resp;
        }
        if (req.getExpectedCurrentState() == null || req.getExpectedCurrentState().trim().isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "缺少 expectedCurrentState 参数");
            return resp;
        }

        try {
            orderMachine.resumeByBusinessId(req.getBusinessId(), req.getExpectedCurrentState(), ctx -> {
                if (req.getShippingAddress() != null && !req.getShippingAddress().trim().isEmpty()) {
                    ctx.setShippingAddress(req.getShippingAddress());
                }
            });
            log.info("[demo] Order resumed: businessId={}", req.getBusinessId());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("businessId", req.getBusinessId());
            resp.put("message", "订单已恢复执行，请查询 /demo/orders 查看最新状态");
            return resp;
        } catch (StateMachineException e) {
            log.error("[demo] Order resume failed: businessId={}", req.getBusinessId(), e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("status", "FAILED");
            resp.put("message", e.getMessage());
            resp.put("stackTrace", stackTrace(e));
            return resp;
        }
    }

    /**
     * 查询最近订单
     */
    @GetMapping("/orders")
    public List<Map<String, Object>> listOrders(@RequestParam(defaultValue = "10") int limit) {
        return instanceRepository.findByMachineName(MachineName.of("order-process"), 0, limit).stream()
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id().value());
                m.put("businessId", r.businessId() != null ? r.businessId().value() : "");
                m.put("status", r.status().name());
                m.put("currentState", r.currentState().value());
                m.put("retryCount", r.retryCount());
                m.put("errorMessage", r.errorMessage() != null ? r.errorMessage() : "");
                m.put("createdAt", r.createdAt().toString());
                return m;
            }).collect(Collectors.toList());
    }

    /**
     * 订单执行详情
     */
    @GetMapping("/orders/{id}")
    public Map<String, Object> orderDetail(@PathVariable String id) {
        Optional<InstanceData> instance = instanceRepository.findById(InstanceId.of(id));
        if (!instance.isPresent()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "Instance not found");
            return resp;
        }

        List<Map<String, Object>> snaps = snapshotRepository.findByInstanceId(InstanceId.of(id)).stream()
            .map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stateName", s.stateName().value());
                m.put("status", s.status().name());
                m.put("attempt", s.attempt());
                m.put("executedAt", s.executedAt().toString());
                m.put("errorMessage", s.errorMessage() != null ? s.errorMessage() : "");
                return m;
            }).collect(Collectors.toList());

        InstanceData inst = instance.get();
        Map<String, Object> instMap = new LinkedHashMap<>();
        instMap.put("id", inst.id().value());
        instMap.put("status", inst.status().name());
        instMap.put("businessId", inst.businessId() != null ? inst.businessId().value() : "");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("instance", instMap);
        resp.put("snapshots", snaps);
        return resp;
    }

    /**
     * 重试失败订单
     */
    @PostMapping("/orders/{id}/retry")
    public Map<String, String> retryOrder(@PathVariable String id) {
        orderMachine.retry(id);
        InstanceData updated = instanceRepository.findById(InstanceId.of(id)).orElse(null);
        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("message", "已重新执行，当前状态: " + (updated != null ? updated.status() : "unknown"));
        return resp;
    }

    // ===== 出库流程 =====

    /**
     * 触发出库流程
     */
    @PostMapping("/outbound")
    public Map<String, Object> createOutbound(@RequestBody OutboundCreateRequest req) {
        String outboundNo = "OB-" + UUID.randomUUID().toString().substring(0, 8);
        String warehouseCode = req.getWarehouseCode() != null && !req.getWarehouseCode().trim().isEmpty() ? req.getWarehouseCode() : "WH01";
        int totalQty = req.getTotalQty() != 0 ? req.getTotalQty() : 100;
        String carrierCode = req.getCarrierCode() != null && !req.getCarrierCode().trim().isEmpty() ? req.getCarrierCode() : "SF";

        OutboundContext ctx = new OutboundContext(outboundNo, warehouseCode, totalQty);
        ctx.setCarrierCode(carrierCode);

        try {
            ExecuteResult result = outboundMachine.execute(ctx, outboundNo);
            log.info("[demo] Outbound created: outboundNo={}, instanceId={}, status={}", outboundNo, result.instanceId(), result.status());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("outboundNo", outboundNo);
            resp.put("instanceId", result.instanceId());
            resp.put("status", result.status());
            resp.put("currentState", result.currentState());
            resp.put("message", result.status());
            return resp;
        } catch (StateMachineException e) {
            log.error("[demo] Outbound creation failed: outboundNo={}", outboundNo, e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "FAILED");
            resp.put("message", e.getMessage());
            resp.put("stackTrace", stackTrace(e));
            return resp;
        }
    }

    /**
     * 恢复挂起的出库实例
     */
    @PostMapping("/outbound/resume")
    public Map<String, Object> resumeOutbound(@RequestBody OutboundResumeRequest req) {
        if (req.getBusinessId() == null || req.getBusinessId().trim().isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "缺少 businessId 参数");
            return resp;
        }
        if (req.getExpectedCurrentState() == null || req.getExpectedCurrentState().trim().isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "缺少 expectedCurrentState 参数");
            return resp;
        }

        try {
            outboundMachine.resumeByBusinessId(req.getBusinessId(), req.getExpectedCurrentState(), ctx -> {
                if (req.getCarrierCode() != null && !req.getCarrierCode().trim().isEmpty()) {
                    ctx.setCarrierCode(req.getCarrierCode());
                }
            });
            log.info("[demo] Outbound resumed: businessId={}", req.getBusinessId());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("businessId", req.getBusinessId());
            resp.put("message", "出库已恢复执行，请查询 /demo/outbounds 查看最新状态");
            return resp;
        } catch (StateMachineException e) {
            log.error("[demo] Outbound resume failed: businessId={}", req.getBusinessId(), e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("status", "FAILED");
            resp.put("message", e.getMessage());
            resp.put("stackTrace", stackTrace(e));
            return resp;
        }
    }

    /**
     * 查询最近出库单
     */
    @GetMapping("/outbounds")
    public List<Map<String, Object>> listOutbounds(@RequestParam(defaultValue = "10") int limit) {
        return instanceRepository.findByMachineName(MachineName.of("outbound-process"), 0, limit).stream()
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id().value());
                m.put("businessId", r.businessId() != null ? r.businessId().value() : "");
                m.put("status", r.status().name());
                m.put("currentState", r.currentState().value());
                m.put("retryCount", r.retryCount());
                m.put("errorMessage", r.errorMessage() != null ? r.errorMessage() : "");
                m.put("createdAt", r.createdAt().toString());
                return m;
            }).collect(Collectors.toList());
    }

    /**
     * 出库执行详情
     */
    @GetMapping("/outbounds/{id}")
    public Map<String, Object> outboundDetail(@PathVariable String id) {
        Optional<InstanceData> instance = instanceRepository.findById(InstanceId.of(id));
        if (!instance.isPresent()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "Instance not found");
            return resp;
        }

        List<Map<String, Object>> snaps = snapshotRepository.findByInstanceId(InstanceId.of(id)).stream()
            .map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stateName", s.stateName().value());
                m.put("status", s.status().name());
                m.put("attempt", s.attempt());
                m.put("executedAt", s.executedAt().toString());
                m.put("errorMessage", s.errorMessage() != null ? s.errorMessage() : "");
                return m;
            }).collect(Collectors.toList());

        InstanceData inst = instance.get();
        Map<String, Object> instMap = new LinkedHashMap<>();
        instMap.put("id", inst.id().value());
        instMap.put("status", inst.status().name());
        instMap.put("businessId", inst.businessId() != null ? inst.businessId().value() : "");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("instance", instMap);
        resp.put("snapshots", snaps);
        return resp;
    }

    /**
     * 重试失败出库单
     */
    @PostMapping("/outbounds/{id}/retry")
    public Map<String, String> retryOutbound(@PathVariable String id) {
        outboundMachine.retry(id);
        InstanceData updated = instanceRepository.findById(InstanceId.of(id)).orElse(null);
        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("message", "已重新执行，当前状态: " + (updated != null ? updated.status() : "unknown"));
        return resp;
    }

    // ===== 收货流程（多挂起点验证） =====

    /**
     * 创建收货单
     */
    @PostMapping("/receiving")
    public Map<String, Object> createReceiving(@RequestBody ReceivingCreateRequest req) {
        String receivingNo = "RCV-" + UUID.randomUUID().toString().substring(0, 8);
        int totalQty = req.getTotalQty() != 0 ? req.getTotalQty() : 100;

        ReceivingContext ctx = new ReceivingContext(receivingNo, totalQty);

        try {
            ExecuteResult result = receivingMachine.execute(ctx, receivingNo);
            log.info("[demo] Receiving created: receivingNo={}, instanceId={}, status={}", receivingNo, result.instanceId(), result.status());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("receivingNo", receivingNo);
            resp.put("instanceId", result.instanceId());
            resp.put("status", result.status());
            resp.put("currentState", result.currentState());
            return resp;
        } catch (StateMachineException e) {
            log.error("[demo] Receiving creation failed: receivingNo={}", receivingNo, e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("status", "FAILED");
            resp.put("message", e.getMessage());
            return resp;
        }
    }

    /**
     * 恢复挂起的收货实例
     */
    @PostMapping("/receiving/resume")
    public Map<String, Object> resumeReceiving(@RequestBody ReceivingResumeRequest req) {
        if (req.getBusinessId() == null || req.getBusinessId().trim().isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "缺少 businessId 参数");
            return resp;
        }
        if (req.getExpectedCurrentState() == null || req.getExpectedCurrentState().trim().isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "缺少 expectedCurrentState 参数");
            return resp;
        }

        try {
            receivingMachine.resumeByBusinessId(req.getBusinessId(), req.getExpectedCurrentState(), ctx -> {
                ctx.setArrivedQty(req.getArrivedQty());
                ctx.setFirstArrived(req.isFirstArrived());
                ctx.setAllArrived(req.isAllArrived());
            });
            log.info("[demo] Receiving resumed: businessId={}", req.getBusinessId());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("businessId", req.getBusinessId());
            resp.put("message", "收货已恢复执行");
            return resp;
        } catch (StateMachineException e) {
            log.error("[demo] Receiving resume failed: businessId={}", req.getBusinessId(), e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("status", "FAILED");
            resp.put("message", e.getMessage());
            return resp;
        }
    }

    /**
     * 查询最近收货单
     */
    @GetMapping("/receivings")
    public List<Map<String, Object>> listReceivings(@RequestParam(defaultValue = "10") int limit) {
        return instanceRepository.findByMachineName(MachineName.of("receiving-process"), 0, limit).stream()
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id().value());
                m.put("businessId", r.businessId() != null ? r.businessId().value() : "");
                m.put("status", r.status().name());
                m.put("currentState", r.currentState().value());
                m.put("retryCount", r.retryCount());
                m.put("errorMessage", r.errorMessage() != null ? r.errorMessage() : "");
                m.put("createdAt", r.createdAt().toString());
                return m;
            }).collect(Collectors.toList());
    }

    /**
     * 收货执行详情
     */
    @GetMapping("/receivings/{id}")
    public Map<String, Object> receivingDetail(@PathVariable String id) {
        Optional<InstanceData> instance = instanceRepository.findById(InstanceId.of(id));
        if (!instance.isPresent()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "Instance not found");
            return resp;
        }

        List<Map<String, Object>> snaps = snapshotRepository.findByInstanceId(InstanceId.of(id)).stream()
            .map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stateName", s.stateName().value());
                m.put("status", s.status().name());
                m.put("attempt", s.attempt());
                m.put("executedAt", s.executedAt().toString());
                m.put("errorMessage", s.errorMessage() != null ? s.errorMessage() : "");
                return m;
            }).collect(Collectors.toList());

        InstanceData inst = instance.get();
        Map<String, Object> instMap = new LinkedHashMap<>();
        instMap.put("id", inst.id().value());
        instMap.put("status", inst.status().name());
        instMap.put("businessId", inst.businessId() != null ? inst.businessId().value() : "");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("instance", instMap);
        resp.put("snapshots", snaps);
        return resp;
    }

    private String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
