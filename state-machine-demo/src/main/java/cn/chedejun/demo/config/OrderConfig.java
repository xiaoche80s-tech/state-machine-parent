package cn.chedejun.demo.config;

import cn.chedejun.demo.statemachine.OrderContext;
import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.StateMachine;
import cn.chedejun.statemachine.core.StateMachineBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class OrderConfig {

    private final AtomicInteger idGen = new AtomicInteger(1);

    @Bean
    public StateMachine<OrderContext> orderMachine() {
        return StateMachineBuilder.<OrderContext>builder("order-process")
            .contextClass(OrderContext.class)
            .state("check-inventory", this::checkInventory)
            .state("process-payment", this::processPayment)
            .state("ship-order", this::shipOrder)
            .state("send-notification", this::sendNotification)
            .state("notify-shortage", this::notifyShortage)
            .state("order-failed", this::orderFailed)

            // 库存充足 -> 支付
            .transition("check-inventory", "process-payment", ctx -> ctx.getStock() > 0)
            // 库存不足 -> 通知缺货
            .transition("check-inventory", "notify-shortage", ctx -> ctx.getStock() <= 0)
            // 支付成功 -> 发货
            .transition("process-payment", "ship-order", ctx -> ctx.isPaymentSuccess())
            // 支付失败 -> 失败
            .transition("process-payment", "order-failed", ctx -> !ctx.isPaymentSuccess())
            // 发货 -> 通知
            .transition("ship-order", "send-notification", ctx -> true)

            .retryPolicy(RetryPolicy.exponentialBackoff()
                .maxAttempts(3)
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(10, TimeUnit.SECONDS)
                .build())
            .build();
    }

    private void checkInventory(OrderContext ctx) {
        log("检查库存: orderId=%s, stock=%d, amount=%.2f", ctx.getOrderId(), ctx.getStock(), ctx.getAmount());
        if (ctx.getStock() < 0) throw new RuntimeException("库存系统连接失败");
    }

    private void processPayment(OrderContext ctx) {
        log("处理支付: orderId=%s, amount=%.2f", ctx.getOrderId(), ctx.getAmount());
        // 模拟 30% 概率支付失败
        if (Math.random() < 0.3) {
            throw new RuntimeException("支付网关超时");
        }
        ctx.setPaymentSuccess(true);
    }

    private void shipOrder(OrderContext ctx) {
        log("订单发货: orderId=%s, address=%s", ctx.getOrderId(), ctx.getShippingAddress());
        if (ctx.getShippingAddress() == null || ctx.getShippingAddress().isEmpty()) {
            throw new RuntimeException("收货地址为空");
        }
    }

    private void sendNotification(OrderContext ctx) {
        log("发送通知: orderId=%s 已发货", ctx.getOrderId());
    }

    private void notifyShortage(OrderContext ctx) {
        log("缺货通知: orderId=%s 库存不足", ctx.getOrderId());
    }

    private void orderFailed(OrderContext ctx) {
        log("订单失败: orderId=%s", ctx.getOrderId());
    }

    private void log(String format, Object... args) {
        System.out.printf("[order-machine] " + format + "%n", args);
    }
}
