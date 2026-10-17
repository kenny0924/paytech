package com.paytech.gateway.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.gateway.guard.OperationsGuardTimingInvariant;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.test.redis.SharedRedisExtension;
import com.paytech.gateway.test.redis.GatewayRedisTestConfiguration;
import com.paytech.gateway.test.redis.RedisTestCleaner;
import com.paytech.gateway.test.redis.RepositoryRedisEnvironment;

/** Gateway 自动化测试基类。 */
@ActiveProfiles("auto")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SharedRedisExtension.class)
@Import({GatewayAutoTestBase.LegacyCatalogTestConfiguration.class, GatewayRedisTestConfiguration.class})
public abstract class GatewayAutoTestBase {
    @org.springframework.beans.factory.annotation.Autowired
    protected RedisTestCleaner redisTestCleaner;

    @org.springframework.beans.factory.annotation.Autowired
    protected RepositoryRedisEnvironment repositoryRedisEnvironment;

    @org.springframework.beans.factory.annotation.Autowired
    GatewaySnapshotRefresher gatewaySnapshotRefresher;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        GatewayAutoTestRuntime.registerProperties(registry);
    }

    @BeforeEach
    void resetGatewayTestState() {
        Assertions.assertAll(
            () -> Assertions.assertTrue(GatewayAutoTestRuntime.clientApiOwnerSimulator().isRunning(),
                "共享 Client API Owner Simulator 在测试类边界已停止"),
            () -> Assertions.assertTrue(GatewayAutoTestRuntime.operationsBffSimulator().isRunning(),
                "共享 BFF Simulator 在测试类边界已停止"),
            () -> Assertions.assertTrue(GatewayAutoTestRuntime.jwksSimulator().isRunning(),
                "共享 JWKS Simulator 在测试类边界已停止"),
            () -> Assertions.assertTrue(GatewayAutoTestRuntime.platformSnapshotSimulator().isRunning(),
                "共享 Platform Snapshot Simulator 在测试类边界已停止"));
        GatewayAutoTestRuntime.platformSnapshotSimulator().reset();
        Assertions.assertTrue(refreshSnapshot(), "默认 Gateway Snapshot 必须在请求测试前加载");
        redisTestCleaner.cleanGuardKeys();
        redisTestCleaner.assertSentinelPresent();
        GatewayAutoTestRuntime.operationsBffSimulator().reset();
        GatewayAutoTestRuntime.clientApiOwnerSimulator().reset();
        GatewayAutoTestRuntime.jwksSimulator().reset();
        GatewayAutoTestRuntime.credentialSimulator().reset();
    }

    protected boolean refreshSnapshot() {
        return Boolean.TRUE
            .equals(reactor.core.publisher.Mono.defer(gatewaySnapshotRefresher::refresh).filter(Boolean.TRUE::equals)
                .repeatWhen(repeats -> repeats.delayElements(java.time.Duration.ofMillis(10)).take(20)).next()
                .defaultIfEmpty(false).block());
    }

    @AfterEach
    void verifyAndResetGatewayTestState() {
        try {
            Assertions.assertTrue(GatewayAutoTestRuntime.clientApiOwnerSimulator().isRunning(),
                "共享 Client API Owner Simulator 在测试结束前被停止");
            GatewayAutoTestRuntime.operationsBffSimulator().verifyComplete();
            GatewayAutoTestRuntime.clientApiOwnerSimulator().verifyComplete();
            GatewayAutoTestRuntime.jwksSimulator().verifyComplete();
            GatewayAutoTestRuntime.credentialSimulator().verifyComplete();
        } finally {
            GatewayAutoTestRuntime.operationsBffSimulator().reset();
            GatewayAutoTestRuntime.clientApiOwnerSimulator().reset();
            GatewayAutoTestRuntime.jwksSimulator().reset();
            GatewayAutoTestRuntime.credentialSimulator().reset();
            GatewayAutoTestRuntime.platformSnapshotSimulator().reset();
            redisTestCleaner.cleanGuardKeys();
            redisTestCleaner.assertSentinelPresent();
        }
    }

    /** 仅保留旧单元断言的构造夹具；生产运行时不存在该 Bean。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class LegacyCatalogTestConfiguration {
        @Bean
        OperationsRouteCatalog legacyOperationsRouteCatalog(OperationsRoutingProperties properties) {
            return new OperationsRouteCatalog(properties.responseTimeout());
        }

        @Bean
        OperationsGuardTimingInvariant legacyOperationsGuardTimingInvariant() {
            return new OperationsGuardTimingInvariant();
        }
    }
}
