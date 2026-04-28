package cn.chedejun.demo.config;

import cn.chedejun.demo.statemachine.OutboundContext;
import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.StateMachine;
import cn.chedejun.statemachine.core.StateMachineBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 出库流程状态机配置
 *
 * 流程：创建出库单 -> 拣货 -> 复核 -> 打包 -> 发货 -> 完成
 * 异常分支：拣货失败 -> 异常处理，复核不通过 -> 重新拣货，发货失败 -> 退货入库
 */
@Configuration
public class OutboundConfig {

    @Bean
    public StateMachine<OutboundContext> outboundMachine() {
        return StateMachineBuilder.<OutboundContext>builder("outbound-process")
            .contextClass(OutboundContext.class)
            .state("create", this::createOutbound)
            .state("pick", this::pickGoods)
            .state("check", this::checkGoods)
            .state("pack", this::packGoods)
            .suspendState("wait-ship-confirm", this::waitShipConfirm)
            .state("ship", this::shipGoods)
            .state("complete", this::completeOutbound)
            .state("handle-exception", this::handleException)
            .state("re-pick", this::rePickGoods)
            .state("return-inbound", this::returnInbound)

            // 正常流程
            .transition("create", "pick", ctx -> true)
            .transition("pick", "check", ctx -> ctx.isPicked())
            // 拣货异常 -> 异常处理
            .transition("pick", "handle-exception", ctx -> !ctx.isPicked())
            .transition("check", "pack", ctx -> ctx.isPacked())
            // 复核不通过 -> 重新拣货
            .transition("check", "re-pick", ctx -> !ctx.isPacked())
            .transition("pack", "wait-ship-confirm", ctx -> true)
            // 挂起点：等待发货确认 -> 发货
            .transition("wait-ship-confirm", "ship", ctx -> true)
            .transition("ship", "complete", ctx -> ctx.isShipped())
            // 发货失败 -> 退货入库
            .transition("ship", "return-inbound", ctx -> !ctx.isShipped())
            // 异常处理/重新拣货/退货入库 -> 完成（终止）
            .transition("handle-exception", "complete", ctx -> true)
            .transition("re-pick", "complete", ctx -> true)
            .transition("return-inbound", "complete", ctx -> true)

            .retryPolicy(RetryPolicy.exponentialBackoff()
                .maxAttempts(3)
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(10, TimeUnit.SECONDS)
                .build())
            .build();
    }

    private void createOutbound(OutboundContext ctx) {
        log("创建出库单: outboundNo=%s, warehouse=%s, qty=%d",
            ctx.getOutboundNo(), ctx.getWarehouseCode(), ctx.getTotalQty());
    }

    private void pickGoods(OutboundContext ctx) {
        log("拣货: outboundNo=%s, qty=%d", ctx.getOutboundNo(), ctx.getTotalQty());
        // 模拟 20% 概率拣货失败
        if (Math.random() < 0.2) {
            throw new RuntimeException("拣货异常：库位不存在");
        }
        ctx.setPicked(true);
    }

    private void checkGoods(OutboundContext ctx) {
        log("复核: outboundNo=%s", ctx.getOutboundNo());
        // 模拟 10% 概率复核不通过
        if (Math.random() < 0.1) {
            log("复核不通过，需要重新拣货: outboundNo=%s", ctx.getOutboundNo());
            ctx.setPacked(false);
        } else {
            ctx.setPacked(true);
        }
    }

    private void waitShipConfirm(OutboundContext ctx) {
        log("等待发货确认: outboundNo=%s, 状态=挂起，等待恢复执行", ctx.getOutboundNo());
    }

    private void packGoods(OutboundContext ctx) {
        log("打包: outboundNo=%s", ctx.getOutboundNo());
    }

    private void shipGoods(OutboundContext ctx) {
        log("发货: outboundNo=%s, carrier=%s", ctx.getOutboundNo(), ctx.getCarrierCode());
        // 模拟 15% 概率发货失败
        if (Math.random() < 0.15) {
            throw new RuntimeException("物流面单获取失败");
        }
        ctx.setShipped(true);
        ctx.setTrackingNo("SF" + System.currentTimeMillis());
    }

    private void completeOutbound(OutboundContext ctx) {
        log("出库完成: outboundNo=%s, tracking=%s", ctx.getOutboundNo(), ctx.getTrackingNo());
    }

    private void handleException(OutboundContext ctx) {
        log("异常处理: outboundNo=%s", ctx.getOutboundNo());
    }

    private void rePickGoods(OutboundContext ctx) {
        log("重新拣货: outboundNo=%s", ctx.getOutboundNo());
        ctx.setPicked(true);
        ctx.setPacked(true);
    }

    private void returnInbound(OutboundContext ctx) {
        log("退货入库: outboundNo=%s", ctx.getOutboundNo());
    }

    private void log(String format, Object... args) {
        LoggerFactory.getLogger(OutboundConfig.class).info("[outbound-machine] " + format, args);
    }
}
