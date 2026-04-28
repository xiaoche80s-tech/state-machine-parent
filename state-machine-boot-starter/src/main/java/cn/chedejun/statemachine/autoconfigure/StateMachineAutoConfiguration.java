package cn.chedejun.statemachine.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.core.StateMachine;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.persistence.DefinitionRepository;
import cn.chedejun.statemachine.persistence.DdlInitializer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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

@AutoConfiguration
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
    public DefinitionRepository definitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new DefinitionRepository(jdbcTemplate, objectMapper);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public StateMachineRegistry stateMachineRegistry(DefinitionRepository definitionRepository) {
        return new StateMachineRegistry(definitionRepository);
    }

    @Bean @ConditionalOnBean(DataSource.class)
    public BeanPostProcessor stateMachineRegistryPostProcessor(StateMachineRegistry registry, JdbcTemplate jdbcTemplate, DdlInitializer ddlInitializer) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof StateMachine<?> machine) {
                    machine.setJdbcTemplate(jdbcTemplate);
                    machine.setRegistry(registry);
                    registry.register(machine);
                    log.info("[state-machine] Auto-configured state machine bean: {}", beanName);
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
        public cn.chedejun.statemachine.management.StateMachineEndpoint stateMachineEndpoint(StateMachineRegistry registry, JdbcTemplate jdbcTemplate) {
            log.info("[state-machine] Management endpoint enabled");
            var endpoint = new cn.chedejun.statemachine.management.StateMachineEndpoint(registry);
            endpoint.setJdbcTemplate(jdbcTemplate);
            return endpoint;
        }
    }

    @Configuration
    @ConditionalOnClass(org.springframework.web.servlet.DispatcherServlet.class)
    @ConditionalOnProperty(prefix = "state-machine.console", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ConsoleConfiguration {
        @Bean
        public cn.chedejun.statemachine.management.ConsoleController consoleController(StateMachineRegistry registry, JdbcTemplate jdbcTemplate) {
            log.info("[state-machine] Console enabled at /statemachine");
            return new cn.chedejun.statemachine.management.ConsoleController(registry, jdbcTemplate);
        }
    }
}
