package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefreshScheduler;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;
import com.paytech.gateway.routing.snapshot.PlatformGatewaySnapshotClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 只验证生产调度器的条件装配，不启用定时执行或外部基础设施。 */
class GatewaySnapshotSchedulingAutoTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(GatewaySnapshotRefresher.class, () -> new GatewaySnapshotRefresher(
            new PlatformGatewaySnapshotClient(WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                Duration.ofSeconds(1)),
            new GatewaySnapshotCompiler(), new GatewaySnapshotStore(),
            new GatewayMetrics(new SimpleMeterRegistry())))
        .withUserConfiguration(GatewaySnapshotRefreshScheduler.class);

    @Test
    void explicitDisableOmitsTheScheduler() {
        contextRunner.withPropertyValues("gateway.platform-snapshot.scheduling-enabled=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(GatewaySnapshotRefreshScheduler.class).isEmpty());
            assertEquals(1, context.getBeansOfType(GatewaySnapshotRefresher.class).size());
        });
    }

    @Test
    void defaultConfigurationKeepsProductionScheduler() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(GatewaySnapshotRefreshScheduler.class).size());
        });
    }

    @Test
    void explicitEnableKeepsProductionScheduler() {
        contextRunner.withPropertyValues("gateway.platform-snapshot.scheduling-enabled=true").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(GatewaySnapshotRefreshScheduler.class).size());
        });
    }
}
