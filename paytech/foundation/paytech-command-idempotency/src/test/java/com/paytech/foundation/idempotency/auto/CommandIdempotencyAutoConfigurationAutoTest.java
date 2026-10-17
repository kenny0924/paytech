package com.paytech.foundation.idempotency.auto;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.paytech.foundation.idempotency.api.CommandIdempotencyService;
import com.paytech.foundation.idempotency.core.autoconfigure.CommandIdempotencyAutoConfiguration;
import com.paytech.foundation.idempotency.persistence.mapper.CommandIdempotencyMapper;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepository;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepositoryImpl;
import com.paytech.foundation.idempotency.service.CommandIdempotencyServiceImpl;

class CommandIdempotencyAutoConfigurationAutoTest {

    @Test
    void test_explicitOptInCreatesMapperRepositoryAndService() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertInstanceOf(CommandIdempotencyServiceImpl.class, context.getBean(CommandIdempotencyService.class));
            assertInstanceOf(CommandIdempotencyRepositoryImpl.class,
                context.getBean(CommandIdempotencyRepository.class));
            assertNotNull(context.getBean(CommandIdempotencyMapper.class));
            Configuration configuration = context.getBean(SqlSessionFactory.class).getConfiguration();
            assertTrue(configurationDirectoryLoaded(configuration));
        }
    }

    @Test
    void test_disabledByDefaultDoesNotCreateCapability() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertTrue(context.getBeansOfType(CommandIdempotencyService.class).isEmpty());
            assertTrue(context.getBeansOfType(CommandIdempotencyRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(CommandIdempotencyMapper.class).isEmpty());
        }
    }

    private AnnotationConfigApplicationContext context(boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (enabled) {
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", java.util.Map.of("command-idempotency.enabled", "true")));
        }
        context.registerBean(SqlSessionFactory.class, () -> {
            Environment environment =
                new Environment("auto-test", new JdbcTransactionFactory(), new UnpooledDataSource());
            return new DefaultSqlSessionFactory(new Configuration(environment));
        });
        context.register(CommandIdempotencyAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private boolean configurationDirectoryLoaded(Configuration configuration) {
        return configuration.hasStatement(CommandIdempotencyMapper.class.getName() + ".insertOrKeepWinner", false)
            && configuration.hasStatement(CommandIdempotencyMapper.class.getName() + ".complete", false);
    }
}
