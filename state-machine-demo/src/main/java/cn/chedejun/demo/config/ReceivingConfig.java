package cn.chedejun.demo.config;

import cn.chedejun.demo.statemachine.ReceivingContext;
import cn.chedejun.statemachine.application.InstanceExecutionService;
import cn.chedejun.statemachine.core.RetryPolicy;
import cn.chedejun.statemachine.core.StateMachineBuilder;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.interfaces.StateMachineFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 收货流程状态机配置
 *
 * 流程：创建收货单 → 等待首批到货(挂起) → 质检 → 等待全部到货(挂起) → 上架 → 完成
 * 验证多挂起点场景：每个挂起点独立控制，通过 resumeCondition 判断是否放行
 */
@Configuration
public class ReceivingConfig {

    @Bean
    public StateMachineFacade<ReceivingContext> receivingMachine(InstanceExecutionService<ReceivingContext> executionService,
                                                                   StateMachineRegistry registry) {
        StateMachine<ReceivingContext> machine = StateMachineBuilder.<ReceivingContext>builder("receiving-process")
            .contextClass(ReceivingContext.class)
            .state("create", this::createReceiving)
            .suspendState("await-first-arrival", this::awaitFirstArrival,
                ctx -> ctx.isFirstArrived())  // resumeCondition: 首批到货
            .state("quality-check", this::qualityCheck)
            .suspendState("await-full-arrival", this::awaitFullArrival,
                ctx -> ctx.isAllArrived())  // resumeCondition: 全部到货
            .state("put-away", this::putAway)
            .state("done", this::done)

            .transition("create", "await-first-arrival", ctx -> true)
            .transition("await-first-arrival", "quality-check", ctx -> true)
            .transition("quality-check", "await-full-arrival", ctx -> true)
            .transition("await-full-arrival", "put-away", ctx -> true)
            .transition("put-away", "done", ctx -> true)

            .retryPolicy(RetryPolicy.exponentialBackoff()
                .maxAttempts(3)
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(10, TimeUnit.SECONDS)
                .build())
            .build();
        registry.register(machine);
        return new StateMachineFacade<>(machine, executionService);
    }

    private void createReceiving(ReceivingContext ctx) {
        log("创建收货单: receivingNo=%s, totalQty=%d", ctx.getReceivingNo(), ctx.getTotalQty());
    }

    private void awaitFirstArrival(ReceivingContext ctx) {
        log("等待首批到货: receivingNo=%s, arrivedQty=%d", ctx.getReceivingNo(), ctx.getArrivedQty());
    }

    private void qualityCheck(ReceivingContext ctx) {
        log("质检: receivingNo=%s", ctx.getReceivingNo());
        ctx.setQualityChecked(true);
    }

    private void awaitFullArrival(ReceivingContext ctx) {
        log("等待全部到货: receivingNo=%s, arrivedQty=%d/%d",
            ctx.getReceivingNo(), ctx.getArrivedQty(), ctx.getTotalQty());
    }

    private void putAway(ReceivingContext ctx) {
        log("上架: receivingNo=%s", ctx.getReceivingNo());
    }

    private void done(ReceivingContext ctx) {
        log("收货完成: receivingNo=%s", ctx.getReceivingNo());
    }

    private void log(String format, Object... args) {
        LoggerFactory.getLogger(ReceivingConfig.class).info("[receiving-machine] " + format, args);
    }
}
