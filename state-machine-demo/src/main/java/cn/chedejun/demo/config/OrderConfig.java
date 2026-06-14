package cn.chedejun.demo.config;

import cn.chedejun.demo.statemachine.OrderContext;
import cn.chedejun.statemachine.application.InstanceExecutionService;
import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.StateMachineBuilder;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.interfaces.StateMachineFacade;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class OrderConfig {

    private final AtomicInteger idGen = new AtomicInteger(1);

    @Bean
    public StateMachineFacade<OrderContext> orderMachine(InstanceExecutionService<OrderContext> executionService,
                                                          StateMachineRegistry registry) {
        StateMachine<OrderContext> machine = StateMachineBuilder.<OrderContext>builder("order-process")
            .contextClass(OrderContext.class)
            .state("check-inventory", this::checkInventory)
            .state("process-payment", this::processPayment)
            .suspendState("await-ship-confirm", this::awaitShipConfirm)
            .state("ship-order", this::shipOrder)
            .state("send-notification", this::sendNotification)
            .state("notify-shortage", this::notifyShortage)
            .state("order-failed", this::orderFailed)
            .transition("check-inventory", "process-payment", ctx -> ctx.getStock() > 0)
            .transition("check-inventory", "notify-shortage", ctx -> ctx.getStock() <= 0)
            .transition("process-payment", "await-ship-confirm", ctx -> ctx.isPaymentSuccess() && !ctx.isRouteFailed() && Math.random() >= 0.5)
            .transition("process-payment", "order-failed", ctx -> !ctx.isPaymentSuccess() || ctx.isRouteFailed())
            .transition("await-ship-confirm", "ship-order", ctx -> true)
            .transition("ship-order", "send-notification", ctx -> true)
            .retryPolicy(RetryPolicy.exponentialBackoff()
                .maxAttempts(3)
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(10, TimeUnit.SECONDS)
                .build())
            .build();
        registry.register(machine);
        return new StateMachineFacade<>(machine, executionService);
    }

    private void checkInventory(OrderContext ctx) {
        log("检查库存：orderId=%s, stock=%d, amount=%.2f", ctx.getOrderId(), ctx.getStock(), ctx.getAmount());
        if (ctx.getStock() < 0) throw new RuntimeException("库存系统连接失败");
    }

    private void processPayment(OrderContext ctx) {
        log("处理支付：orderId=%s, amount=%.2f", ctx.getOrderId(), ctx.getAmount());
        // 30% 机率执行失败（action 抛异常，触发重试）
        if (Math.random() < 0.3) {
            throw new RuntimeException("支付网关超时（模拟30%失败率）");
        }
        ctx.setPaymentSuccess(true);
    }

    private void awaitShipConfirm(OrderContext ctx) {
        // 此 action 在 resume 时执行，而非进入挂起状态时
        // 业务场景：外部信号恢复后的确认处理
        log("发货确认恢复处理：orderId=%s", ctx.getOrderId());
    }

    private void shipOrder(OrderContext ctx) {
        log("订单发货：orderId=%s, address=%s", ctx.getOrderId(), ctx.getShippingAddress());
        if (ctx.getShippingAddress() == null || ctx.getShippingAddress().isEmpty()) {
            throw new RuntimeException("收货地址为空");
        }
    }

    private void sendNotification(OrderContext ctx) {
        log("发送通知：orderId=%s 已发货", ctx.getOrderId());
    }

    private void notifyShortage(OrderContext ctx) {
        log("缺货通知：orderId=%s 库存不足", ctx.getOrderId());
    }

    private void orderFailed(OrderContext ctx) {
        log("订单失败：orderId=%s", ctx.getOrderId());
    }

    private void log(String format, Object... args) {
        LoggerFactory.getLogger(OrderConfig.class).info("[order-machine] " + format, args);
    }
}
