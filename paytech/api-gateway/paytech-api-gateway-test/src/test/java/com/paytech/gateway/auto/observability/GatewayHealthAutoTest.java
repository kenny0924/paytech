package com.paytech.gateway.auto.observability;

import com.paytech.gateway.test.GatewayPublicTestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.redis.SharedRedisExtension;
import com.paytech.gateway.test.redis.GatewayRedisTestConfiguration;
import com.paytech.gateway.test.redis.RedisTestCleaner;

/** Gateway Management 端口、健康组与业务端口隔离契约测试。 */
@ActiveProfiles("auto")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SharedRedisExtension.class)
@Import(GatewayRedisTestConfiguration.class)
class GatewayHealthAutoTest {
    @Autowired
    private RedisTestCleaner redisTestCleaner;

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private OperationsRoutingProperties routingProperties;

    @Autowired
    private GatewaySnapshotRefresher snapshotRefresher;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        GatewayAutoTestRuntime.registerProperties(registry, URI.create("http://127.0.0.1:1"));
    }

    @BeforeEach
    void resetGatewayTestState() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().reset();
        Boolean loaded = reactor.core.publisher.Mono.defer(snapshotRefresher::refresh).filter(Boolean.TRUE::equals)
            .repeatWhen(repeats -> repeats.delayElements(Duration.ofMillis(10)).take(20)).next().defaultIfEmpty(false)
            .block();
        Assertions.assertEquals(Boolean.TRUE, loaded, "Management 隔离上下文必须加载自己的 Snapshot");
        redisTestCleaner.cleanGuardKeys();
        redisTestCleaner.assertSentinelPresent();
        GatewayAutoTestRuntime.operationsBffSimulator().reset();
        GatewayAutoTestRuntime.jwksSimulator().reset();
    }

    @AfterEach
    void verifyAndResetGatewayTestState() {
        try {
            GatewayAutoTestRuntime.operationsBffSimulator().verifyComplete();
            GatewayAutoTestRuntime.jwksSimulator().verifyComplete();
        } finally {
            GatewayAutoTestRuntime.operationsBffSimulator().reset();
            GatewayAutoTestRuntime.jwksSimulator().reset();
            GatewayAutoTestRuntime.platformSnapshotSimulator().reset();
            redisTestCleaner.cleanGuardKeys();
            redisTestCleaner.assertSentinelPresent();
        }
    }

    @Test
    void test_readinessEndpointReportsReadyWhenSnapshotAndRedisAreAvailable() {
        managementClient().get().uri("/actuator/health/readiness").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("UP").jsonPath("$.components.gatewayBootstrap.status").isEqualTo("UP")
            .jsonPath("$.components.gatewayRedis.status").isEqualTo("UP");

        Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_livenessEndpointReportsOnlyProcessAvailability() {
        managementClient().get().uri("/actuator/health/liveness").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void test_managementEndpointsAndApplicationIngressAreDeniedByDefault() {
        managementClient().get().uri("/actuator/health").exchange().expectStatus().isUnauthorized();
        managementClient().get().uri("/actuator/info").exchange().expectStatus().isUnauthorized();
        managementClient().get().uri("/actuator/prometheus").exchange().expectStatus().isOk().expectBody(String.class)
            .value(body -> Assertions.assertTrue(body.contains("gateway_snapshot_valid")));
        managementClient().get().uri("/actuator").exchange().expectStatus().isUnauthorized();

        WebTestClient applicationClient = GatewayPublicTestClient.operations(applicationPort)
            .defaultHeader("X-Real-IP", "203.0.113.17").responseTimeout(Duration.ofSeconds(5)).build();
        applicationClient.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
        applicationClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isNotFound();
        applicationClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isNotFound();
        applicationClient.get().uri("/actuator/info").exchange().expectStatus().isNotFound();
        applicationClient.get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
        applicationClient.get().uri("/api/operations/actuator/health").exchange().expectStatus().isNotFound()
            .expectBody().returnResult();
        applicationClient.get().uri("/api/operations/actuator/prometheus").exchange().expectStatus().isNotFound()
            .expectBody().returnResult();

        Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_productionConfigurationBindsDedicatedApplicationAndManagementPorts() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
            .addFirst(new MapPropertySource("gateway-bind-test", Map.of("GATEWAY_BIND_ADDRESS", "127.0.0.1")));
        new YamlPropertySourceLoader().load("gateway-production", new ClassPathResource("application.yml"))
            .forEach(propertySource -> environment.getPropertySources().addLast(propertySource));
        Binder binder = Binder.get(environment);

        ServerProperties server = binder.bindOrCreate("server", ServerProperties.class);
        ManagementServerProperties management =
            binder.bindOrCreate("management.server", ManagementServerProperties.class);

        Assertions.assertEquals(18080, server.getPort());
        Assertions.assertEquals(19080, management.getPort());
        Assertions
            .assertFalse(environment.getRequiredProperty("management.endpoints.enabled-by-default", Boolean.class));
        Assertions
            .assertFalse(environment.getRequiredProperty("management.endpoints.web.discovery.enabled", Boolean.class));
        Assertions.assertEquals("health,info,prometheus",
            environment.getRequiredProperty("management.endpoints.web.exposure.include"));
        Assertions.assertEquals("*", environment.getRequiredProperty("management.endpoints.jmx.exposure.exclude"));
        Assertions.assertFalse(
            environment.getRequiredProperty("management.endpoint.health.probes.add-additional-paths", Boolean.class));
        Assertions.assertEquals("livenessState",
            environment.getRequiredProperty("management.endpoint.health.group.liveness.include"));
        Assertions.assertEquals("readinessState,gatewayBootstrap,gatewayRedis",
            environment.getRequiredProperty("management.endpoint.health.group.readiness.include"));
        Assertions.assertFalse(environment.getRequiredProperty("management.health.redis.enabled", Boolean.class));
    }

    private WebTestClient managementClient() {
        return client(managementPort);
    }

    private WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).responseTimeout(Duration.ofSeconds(5))
            .build();
    }
}
