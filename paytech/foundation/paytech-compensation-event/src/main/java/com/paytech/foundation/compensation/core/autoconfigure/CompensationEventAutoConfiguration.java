package com.paytech.foundation.compensation.core.autoconfigure;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.*;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;
import com.paytech.foundation.compensation.core.execution.*;
import com.paytech.foundation.compensation.core.observability.CompensationEventObserver;
import com.paytech.foundation.compensation.core.observability.MicrometerCompensationEventObserver;
import com.paytech.foundation.compensation.core.serialization.CompensationPayloadPolicy;
import com.paytech.foundation.compensation.core.serialization.CompensationPayloadSerializer;
import com.paytech.foundation.compensation.core.transaction.CompensationTransactionBoundary;
import com.paytech.foundation.compensation.persistence.mapper.CompensationEventMapper;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepository;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepositoryImpl;
import com.paytech.foundation.compensation.service.CompensationEventRecoveryServiceImpl;
import com.paytech.foundation.compensation.service.CompensationEventRegistrationService;
import com.paytech.foundation.id.IdGenerator;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * 服务本地补偿任务的 Spring Boot 自动配置。
 *
 * @author Codex
 */
@AutoConfiguration
@AutoConfigureAfter({MybatisAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
@ConditionalOnClass({SqlSessionFactory.class, CompensationEventRegistrar.class})
@ConditionalOnProperty(prefix = "compensation", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CompensationEventProperties.class)
public class CompensationEventAutoConfiguration {

    private static final String MAPPER_RESOURCE = "META-INF/mybatis/CompensationEventMapper.xml";
    private static final String COORDINATOR_EXECUTOR_BEAN = "compensationCoordinatorExecutor";
    private static final String HANDLER_EXECUTOR_BEAN = "compensationHandlerExecutor";
    private static final String OBSERVER_BEAN = "compensationEventObserver";
    private static final String HANDLER_REGISTRY_BEAN = "compensationHandlerRegistry";
    private static final String DISPATCHER_BEAN = "compensationEventDispatcher";
    private static final String TRANSACTION_BOUNDARY_BEAN = "compensationTransactionBoundary";

    @Bean
    ValidatedCompensationConfiguration compensationRuntimeConfiguration(CompensationEventProperties properties,
        Environment environment) {
        applyInstanceIdDefault(properties, environment);
        validateProperties(properties);
        return new ValidatedCompensationConfiguration(properties.getInstanceId(), properties.getLeaseDuration(),
            properties.getScannerBatchSize(), properties.getCoordinator().getCoreSize(),
            properties.getCoordinator().getMaximumSize(), properties.getCoordinator().getQueueCapacity(),
            properties.getCoordinator().getKeepAlive(), properties.getHandler().getMaximumConcurrency());
    }

    @Bean(name = HANDLER_REGISTRY_BEAN)
    CompensationHandlerRegistry compensationHandlerRegistry(ObjectProvider<CompensationEventHandler> handlerProvider,
        ValidatedCompensationConfiguration configuration) {
        List<CompensationEventHandler> handlers = handlerProvider.orderedStream().toList();
        return new CompensationHandlerRegistry(handlers, configuration.leaseDuration());
    }

    @Bean
    CompensationPayloadSerializer compensationPayloadSerializer() {
        return new CompensationPayloadSerializer(JsonMapper.builder().build(), new CompensationPayloadPolicy());
    }

    @Bean
    @ConditionalOnMissingBean(CompensationEventMapper.class)
    MapperFactoryBean<CompensationEventMapper> compensationEventMapper(SqlSessionFactory sqlSessionFactory) {
        // 泛型 Handler 按 Java 枚举类型绑定，不能依赖 MyBatis 对 Handler Class 的最后一个实例缓存。
        sqlSessionFactory.getConfiguration().getTypeHandlerRegistry().register(
            com.paytech.foundation.compensation.model.CompensationEventStatus.class,
            com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler.class);
        loadMapperXml(sqlSessionFactory);
        MapperFactoryBean<CompensationEventMapper> factoryBean = new MapperFactoryBean<>(CompensationEventMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        factoryBean.setAddToConfig(false);
        return factoryBean;
    }

    @Bean(name = TRANSACTION_BOUNDARY_BEAN)
    CompensationTransactionBoundary compensationTransactionBoundary(SqlSessionFactory sqlSessionFactory,
        PlatformTransactionManager transactionManager) {
        return new CompensationTransactionBoundary(sqlSessionFactory, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(CompensationEventRepository.class)
    CompensationEventRepository compensationEventRepository(CompensationEventMapper mapper,
        PlatformTransactionManager transactionManager) {
        return new CompensationEventRepositoryImpl(mapper, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    RetryDelayCalculator compensationRetryDelayCalculator() {
        return new RetryDelayCalculator();
    }

    @Bean
    @ConditionalOnMissingBean
    SafeErrorSummary compensationSafeErrorSummary() {
        return new SafeErrorSummary();
    }

    @Bean(name = OBSERVER_BEAN)
    @ConditionalOnMissingBean(CompensationEventObserver.class)
    CompensationEventObserver compensationEventObserver(MeterRegistry meterRegistry) {
        return new MicrometerCompensationEventObserver(meterRegistry);
    }

    @Bean(name = COORDINATOR_EXECUTOR_BEAN, destroyMethod = "shutdownNow")
    ThreadPoolExecutor
        compensationCoordinatorExecutor(ValidatedCompensationConfiguration configuration) {
        BlockingQueue<Runnable> queue = configuration.coordinatorQueueCapacity() == 0 ? new SynchronousQueue<>()
            : new ArrayBlockingQueue<>(configuration.coordinatorQueueCapacity());
        RejectedExecutionHandler rejectionHandler = new ThreadPoolExecutor.AbortPolicy();
        return new ThreadPoolExecutor(configuration.coordinatorCoreSize(), configuration.coordinatorMaximumSize(),
            configuration.coordinatorKeepAlive().toMillis(), TimeUnit.MILLISECONDS, queue,
            namedDaemonThreadFactory("compensation-coordinator-"), rejectionHandler);
    }

    @Bean(name = HANDLER_EXECUTOR_BEAN, destroyMethod = "shutdownNow")
    ExecutorService compensationHandlerExecutor(ValidatedCompensationConfiguration configuration) {
        int maximumSize = configuration.handlerMaximumConcurrency();
        return new ThreadPoolExecutor(maximumSize, maximumSize, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
            namedDaemonThreadFactory("compensation-handler-"), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = DISPATCHER_BEAN)
    CompensationEventDispatcher compensationEventDispatcher(
        @Qualifier(HANDLER_EXECUTOR_BEAN) ExecutorService handlerExecutor, SafeErrorSummary safeErrorSummary,
        @Qualifier(COORDINATOR_EXECUTOR_BEAN) ThreadPoolExecutor coordinatorExecutor,
        RetryDelayCalculator retryDelayCalculator, CompensationEventObserver observer,
        CompensationEventRepository eventRepository,
        @Qualifier(HANDLER_REGISTRY_BEAN) CompensationHandlerRegistry handlerRegistry,
        ValidatedCompensationConfiguration configuration) {
        return new CompensationEventDispatcher(handlerExecutor, safeErrorSummary, coordinatorExecutor,
            retryDelayCalculator, observer, eventRepository, handlerRegistry, configuration.handlerMaximumConcurrency(),
            configuration.instanceId(), configuration.leaseDuration());
    }

    @Bean
    SmartInitializingSingleton
        compensationWakeupTopologyValidator(ObjectProvider<CompensationEventWakeup> wakeupProvider) {
        return () -> {
            if (wakeupProvider.stream().limit(2L).count() > 1L) {
                throw configurationError("multiple CompensationEventWakeup beans are not allowed");
            }
        };
    }

    @Bean
    @ConditionalOnBean(CompensationEventDispatcher.class)
    @ConditionalOnMissingBean(CompensationEventScanner.class)
    CompensationEventScanner compensationEventScanner(CompensationEventObserver observer,
        @Qualifier(DISPATCHER_BEAN) CompensationEventDispatcher dispatcher, CompensationEventRepository eventRepository,
        ValidatedCompensationConfiguration configuration) {
        return new CompensationEventScannerService(observer, dispatcher, eventRepository,
            configuration.scannerBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean
    CompensationAfterCommitWakeup
        compensationAfterCommitWakeup(ObjectProvider<CompensationEventWakeup> wakeupProvider) {
        return new CompensationAfterCommitWakeup(wakeupProvider);
    }

    @Bean
    @ConditionalOnMissingBean(CompensationEventRegistrar.class)
    CompensationEventRegistrar compensationEventRegistrar(IdGenerator idGenerator, CompensationEventObserver observer,
        CompensationEventRepository eventRepository,
        @Qualifier(HANDLER_REGISTRY_BEAN) CompensationHandlerRegistry handlerRegistry,
        CompensationPayloadSerializer payloadSerializer,
        @Qualifier(TRANSACTION_BOUNDARY_BEAN) CompensationTransactionBoundary transactionBoundary,
        CompensationAfterCommitWakeup afterCommitWakeup) {
        return new CompensationEventRegistrationService(idGenerator, observer, eventRepository, handlerRegistry,
            payloadSerializer, transactionBoundary, afterCommitWakeup);
    }

    @Bean
    @ConditionalOnBean(CompensationRecoveryAuditRecorder.class)
    @ConditionalOnMissingBean(CompensationEventRecoveryService.class)
    CompensationEventRecoveryService compensationEventRecoveryService(CompensationEventObserver observer,
        CompensationEventRepository eventRepository,
        @Qualifier(HANDLER_REGISTRY_BEAN) CompensationHandlerRegistry handlerRegistry,
        @Qualifier(TRANSACTION_BOUNDARY_BEAN) CompensationTransactionBoundary transactionBoundary,
        CompensationAfterCommitWakeup afterCommitWakeup, CompensationRecoveryAuditRecorder auditRecorder) {
        return new CompensationEventRecoveryServiceImpl(observer, eventRepository, handlerRegistry, transactionBoundary,
            afterCommitWakeup, auditRecorder);
    }

    private void loadMapperXml(SqlSessionFactory sqlSessionFactory) {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        synchronized (configuration) {
            if (configuration.hasStatement(CompensationEventMapper.class.getName() + ".insert", false)) {
                return;
            }
            try (var inputStream = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
                XMLMapperBuilder mapperBuilder =
                    new XMLMapperBuilder(inputStream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments());
                mapperBuilder.parse();
            } catch (IOException exception) {
                throw new FailException(CompensationErrorCode.CONFIGURATION_INVALID,
                    "cannot load compensation MyBatis mapper: " + MAPPER_RESOURCE, exception);
            }
        }
    }

    private void validateProperties(CompensationEventProperties properties) {
        requireText("instance-id", properties.getInstanceId(), 64);
        if (properties.getLeaseDuration() == null || properties.getLeaseDuration().isZero()
            || properties.getLeaseDuration().isNegative()) {
            throw configurationError("lease-duration must be positive");
        }
        if (properties.getScannerBatchSize() <= 0) {
            throw configurationError("scanner-batch-size must be positive");
        }
        validateCoordinatorPool(properties.getCoordinator());
        validateHandlerPool(properties.getHandler());
    }

    private void applyInstanceIdDefault(CompensationEventProperties properties, Environment environment) {
        if (properties.getInstanceId() != null && !properties.getInstanceId().isBlank()) {
            properties.setInstanceId(properties.getInstanceId().trim());
            return;
        }
        String applicationName = environment.getProperty("spring.application.name");
        properties.setInstanceId(
            applicationName == null || applicationName.isBlank() ? "application" : applicationName.trim());
    }

    private void validateCoordinatorPool(CompensationEventProperties.CoordinatorPool pool) {
        if (pool.getCoreSize() <= 0 || pool.getMaximumSize() < pool.getCoreSize()) {
            throw configurationError("coordinator requires 0 < core-size <= maximum-size");
        }
        if (pool.getQueueCapacity() < 0) {
            throw configurationError("coordinator.queue-capacity must not be negative");
        }
        if (pool.getKeepAlive() == null || pool.getKeepAlive().isNegative()) {
            throw configurationError("coordinator.keep-alive must not be negative");
        }
    }

    private void validateHandlerPool(CompensationEventProperties.HandlerPool pool) {
        if (pool.getMaximumConcurrency() <= 0) {
            throw configurationError("handler.maximum-concurrency must be positive");
        }
    }

    private void requireText(String property, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw configurationError(property + " must not be blank");
        }
        if (value.trim().length() > maximumLength) {
            throw configurationError(property + " exceeds " + maximumLength + " characters");
        }
    }

    private FailException configurationError(String message) {
        return new FailException(CompensationErrorCode.CONFIGURATION_INVALID, "compensation." + message);
    }

    private ThreadFactory namedDaemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record ValidatedCompensationConfiguration(String instanceId, Duration leaseDuration, int scannerBatchSize,
        int coordinatorCoreSize, int coordinatorMaximumSize, int coordinatorQueueCapacity,
        Duration coordinatorKeepAlive, int handlerMaximumConcurrency) {
    }
}
