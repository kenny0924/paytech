package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.compensation.core.observability.MicrometerCompensationEventObserver;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 指标命名测试。
 *
 * @author Codex
 */
class MicrometerCompensationEventObserverAutoTest {

    @Test
    void test_metricsDoNotUseProjectPrefix() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCompensationEventObserver observer = new MicrometerCompensationEventObserver(registry);

        observer.registrationFinished("TestEvent", false);
        observer.attemptFinished("TestEvent", "success", Duration.ofMillis(10));

        assertNotNull(registry.find("compensation.registrations").counter());
    }
}
