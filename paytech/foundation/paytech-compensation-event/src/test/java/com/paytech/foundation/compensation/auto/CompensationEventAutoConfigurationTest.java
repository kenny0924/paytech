package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.paytech.foundation.compensation.core.autoconfigure.CompensationEventProperties;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepository;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepositoryImpl;
import com.paytech.foundation.compensation.test.base.MockTestBase;

/**
 * 共用自动配置冒烟测试。
 *
 * @author Codex
 */
class CompensationEventAutoConfigurationTest extends MockTestBase {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    CompensationEventProperties properties;

    @Autowired
    CompensationEventRepository eventRepository;

    @Test
    void test_commonApplicationCreatesNamedCoreBeans() {
        assertSame(applicationContext.getBean("compensationEventDispatcher"),
            applicationContext.getBean(CompensationEventDispatcher.class));
        assertSame(applicationContext.getBean("compensationEventRepository"), eventRepository);
        assertInstanceOf(CompensationEventRepositoryImpl.class, eventRepository);
        assertEquals("compensation-test", properties.getInstanceId());
        assertEquals(Duration.ofMinutes(2), properties.getLeaseDuration());
        assertEquals(100, properties.getScannerBatchSize());
        assertEquals(10, properties.getCoordinator().getCoreSize());
        assertEquals(20, properties.getCoordinator().getMaximumSize());
        assertEquals(80, properties.getCoordinator().getQueueCapacity());
        assertEquals(Duration.ofSeconds(60), properties.getCoordinator().getKeepAlive());
        assertEquals(20, properties.getHandler().getMaximumConcurrency());

        ThreadPoolExecutor coordinator = assertInstanceOf(ThreadPoolExecutor.class,
            applicationContext.getBean("compensationCoordinatorExecutor"));
        assertEquals(10, coordinator.getCorePoolSize());
        assertEquals(20, coordinator.getMaximumPoolSize());
        assertEquals(80, coordinator.getQueue().remainingCapacity());
        assertEquals(60L, coordinator.getKeepAliveTime(TimeUnit.SECONDS));
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, coordinator.getRejectedExecutionHandler());

        ThreadPoolExecutor handler = assertInstanceOf(ThreadPoolExecutor.class,
            applicationContext.getBean("compensationHandlerExecutor"));
        assertEquals(20, handler.getCorePoolSize());
        assertEquals(20, handler.getMaximumPoolSize());
        assertInstanceOf(SynchronousQueue.class, handler.getQueue());
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, handler.getRejectedExecutionHandler());
    }
}
