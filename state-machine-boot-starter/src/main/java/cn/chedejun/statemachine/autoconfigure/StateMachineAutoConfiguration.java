package cn.chedejun.statemachine.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.application.InstanceExecutionService;
import cn.chedejun.statemachine.core.StateMachineBuilder;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.repository.DefinitionRepository;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.infrastructure.persistence.JdbcDefinitionRepository;
import cn.chedejun.statemachine.infrastructure.persistence.JdbcInstanceRepository;
import cn.chedejun.statemachine.infrastructure.persistence.JdbcSnapshotRepository;
import cn.chedejun.statemachine.interfaces.StateMachineFacade;
import cn.chedejun.statemachine.persistence.DdlInitializer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.util.List;

@Configuration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(StateMachineProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
public class StateMachineAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(StateMachineAutoConfiguration.class);

    @Bean @ConditionalOnBean(DataSource.class)
    public DdlInitializer ddlInitializer(DataSource dataSource, StateMachineProperties properties) {
        return new DdlInitializer(dataSource, properties.getDdlAuto());
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public InstanceRepository instanceRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcInstanceRepository(jdbcTemplate);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public SnapshotRepository snapshotRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcSnapshotRepository(jdbcTemplate);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public DefinitionRepository definitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcDefinitionRepository(jdbcTemplate, objectMapper);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public StateMachineRegistry stateMachineRegistry(DefinitionRepository definitionRepository) {
        return new StateMachineRegistry(definitionRepository);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public InstanceExecutionService instanceExecutionService(InstanceRepository instanceRepo,
                                                              SnapshotRepository snapshotRepo,
                                                              DefinitionRepository definitionRepo,
                                                              ObjectMapper objectMapper) {
        return new InstanceExecutionService(instanceRepo, snapshotRepo, definitionRepo, objectMapper);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public BeanPostProcessor stateMachineRegistryPostProcessor(StateMachineRegistry registry,
                                                                 InstanceExecutionService executionService,
                                                                 JdbcTemplate jdbcTemplate) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof StateMachineBuilder<?>) {
                    @SuppressWarnings("unchecked")
                    StateMachineBuilder<Object> builder = (StateMachineBuilder<Object>) bean;
                    builder.jdbcTemplate(jdbcTemplate);
                    builder.registry(registry);
                    return bean;
                }
                if (bean instanceof StateMachineFacade<?>) {
                    log.info("[state-machine] 自动配置门面: {}", beanName);
                    return bean;
                }
                return bean;
            }
        };
    }

    @Bean
    public org.springframework.beans.factory.config.BeanPostProcessor stateMachineBeanRegistrar(StateMachineRegistry registry) {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof cn.chedejun.statemachine.domain.engine.StateMachine<?>) {
                    @SuppressWarnings("unchecked")
                    cn.chedejun.statemachine.domain.engine.StateMachine<Object> machine =
                        (cn.chedejun.statemachine.domain.engine.StateMachine<Object>) bean;
                    registry.register(machine);
                }
                return bean;
            }
        };
    }

    @Configuration
    @ConditionalOnClass(org.springframework.boot.actuate.endpoint.annotation.Endpoint.class)
    @ConditionalOnProperty(prefix = "state-machine.management", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ManagementConfiguration {
        @Bean
        public cn.chedejun.statemachine.management.StateMachineEndpoint stateMachineEndpoint(
                StateMachineRegistry registry,
                InstanceRepository instanceRepository,
                DefinitionRepository definitionRepository) {
            log.info("[state-machine] 管理端点已启用");
            return new cn.chedejun.statemachine.management.StateMachineEndpoint(registry, instanceRepository, definitionRepository);
        }
    }

    @Configuration
    @ConditionalOnClass(org.springframework.web.servlet.DispatcherServlet.class)
    @ConditionalOnProperty(prefix = "state-machine.console", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ConsoleConfiguration {
        @Bean
        public cn.chedejun.statemachine.management.ConsoleController consoleController(
                StateMachineRegistry registry,
                InstanceRepository instanceRepository,
                SnapshotRepository snapshotRepository,
                InstanceExecutionService<Object> executionService) {
            log.info("[state-machine] 控制台已启用 /statemachine");
            return new cn.chedejun.statemachine.management.ConsoleController(
                registry, instanceRepository, snapshotRepository, executionService);
        }
    }
}
