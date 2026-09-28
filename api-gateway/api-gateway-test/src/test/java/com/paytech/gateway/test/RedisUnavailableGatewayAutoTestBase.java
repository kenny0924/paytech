package com.paytech.gateway.test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.ApiGatewayApplication;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.redis.RedisTestNamespace;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.test.simulator.jwks.JwksSimulator;
import com.paytech.gateway.test.simulator.platform.PlatformSnapshotSimulator;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;

/** 只连接关闭 Loopback Redis 端口的私有 Gateway 自动测试基类。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RedisUnavailableGatewayAutoTestBase {
    private OperationsBffSimulator operationsBffSimulator;
    private JwksSimulator jwksSimulator;
    private PlatformSnapshotSimulator platformSnapshotSimulator;
    private PlatformCredentialValidationSimulator credentialSimulator;
    private ConfigurableApplicationContext applicationContext;
    private WebTestClient gatewayClient;
    private WebTestClient managementClient;
    private int closedRedisPort;

    private static int allocateClosedLoopbackPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @BeforeAll
    void startPrivateGateway() throws Exception {
        closedRedisPort = allocateClosedLoopbackPort();
        operationsBffSimulator = new OperationsBffSimulator();
        operationsBffSimulator.start();
        jwksSimulator = new JwksSimulator();
        jwksSimulator.start();
        platformSnapshotSimulator = new PlatformSnapshotSimulator(GatewaySnapshotFixture.TEST_ROUTE_RESPONSE_TIMEOUT);
        platformSnapshotSimulator.start();
        credentialSimulator = new PlatformCredentialValidationSimulator();
        credentialSimulator.start();

        SpringApplication application = new SpringApplication(ApiGatewayApplication.class);
        application.setRegisterShutdownHook(false);
        application.addInitializers(context -> context.getBeanFactory()
            .registerSingleton(PrivateReactorResources.class.getName(), new PrivateReactorResources()));
        applicationContext = application.run("--spring.profiles.active=auto", "--gateway.test.redis.scenario=unavailable",
            "--spring.cloud.discovery.client.simple.instances.operations-console-bff[0].uri="
                + operationsBffSimulator.uri(),
            "--gateway.platform-snapshot.base-uri=" + platformSnapshotSimulator.uri(),
            "--gateway.platform-jwks.base-uri=" + jwksSimulator.uri(),
            "--gateway.platform-credential.base-uri=" + credentialSimulator.uri(),
            "--gateway.operations.guard.redis.namespace="
                + RedisTestNamespace.from(UUID.randomUUID()).value(),
            "--spring.data.redis.port=" + closedRedisPort);
        loadInitialSnapshot(applicationContext.getBean(GatewaySnapshotRefresher.class));
        int gatewayPort = applicationContext.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        int managementPort =
            applicationContext.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
        gatewayClient = GatewayPublicTestClient.operations(gatewayPort)
            .defaultHeader("X-Real-IP", "203.0.113.17").responseTimeout(java.time.Duration.ofSeconds(5)).build();
        managementClient = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort)
            .responseTimeout(java.time.Duration.ofSeconds(5)).build();
    }

    private void loadInitialSnapshot(GatewaySnapshotRefresher refresher) {
        Boolean loaded = reactor.core.publisher.Mono.defer(refresher::refresh).filter(Boolean.TRUE::equals)
            .repeatWhen(repeats -> repeats.delayElements(java.time.Duration.ofMillis(10)).take(20)).next()
            .defaultIfEmpty(false).block();
        Assertions.assertEquals(Boolean.TRUE, loaded, "私有 Gateway 初始 Snapshot 加载失败");
    }

    @BeforeEach
    void resetPrivateSimulators() {
        operationsBffSimulator.reset();
        jwksSimulator.reset();
        credentialSimulator.reset();
    }

    @AfterEach
    void verifyPrivateSimulators() {
        try {
            operationsBffSimulator.verifyComplete();
            jwksSimulator.verifyComplete();
            credentialSimulator.verifyComplete();
        } finally {
            operationsBffSimulator.reset();
            jwksSimulator.reset();
            credentialSimulator.reset();
        }
    }

    @AfterAll
    void stopPrivateGateway() {
        if (applicationContext != null) {
            awaitRedisHandshakeTimeoutDrain();
            applicationContext.close();
        }
        if (operationsBffSimulator != null) {
            operationsBffSimulator.stop();
        }
        if (jwksSimulator != null) {
            jwksSimulator.stop();
        }
        if (platformSnapshotSimulator != null) {
            platformSnapshotSimulator.stop();
        }
        if (credentialSimulator != null) {
            credentialSimulator.close();
        }
    }

    protected final WebTestClient gatewayClient() {
        return gatewayClient;
    }

    protected final WebTestClient managementClient() {
        return managementClient;
    }

    protected final int closedRedisPort() {
        return closedRedisPort;
    }

    protected final OperationsBffSimulator operationsBffSimulator() {
        return operationsBffSimulator;
    }

    protected final JwksSimulator jwksSimulator() {
        return jwksSimulator;
    }

    private void awaitRedisHandshakeTimeoutDrain() {
        org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connectionFactory =
            applicationContext
                .getBean(org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.class);
        CountDownLatch drained = new CountDownLatch(1);
        connectionFactory.getRequiredNativeClient().getResources().timer().newTimeout(ignored -> drained.countDown(),
            50, TimeUnit.MILLISECONDS);
        try {
            Assertions.assertTrue(drained.await(1, TimeUnit.SECONDS), "私有 Redis Handshake Timeout 任务未在关闭前排空");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待私有 Redis Handshake Timeout 时被中断", exception);
        }
    }

    /** 防止私有 Context 关闭时销毁同 JVM 中健康测试已启动的 Reactor Netty 全局资源。 */
    private static final class PrivateReactorResources implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof ReactorResourceFactory resourceFactory) {
                resourceFactory.setUseGlobalResources(false);
            }
            return bean;
        }
    }
}
