package com.paytech.gateway.auto.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

import com.paytech.gateway.configuration.GatewaySnapshotConfiguration;
import com.paytech.gateway.configuration.PlatformSnapshotProperties;
import com.paytech.gateway.routing.snapshot.PlatformGatewaySnapshotClient;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.platform.PlatformSnapshotSimulator;

import reactor.test.StepVerifier;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.json.JsonMapper;

/**
 * PLT-I20 Snapshot 客户端的配置上限、JSON 嵌套上限与 HTTP 状态判定。
 *
 * @see PlatformGatewaySnapshotClient#fetch()
 * @see PlatformSnapshotProperties#PlatformSnapshotProperties(URI, String, boolean, Duration, Duration, int, Duration)
 */
class PlatformSnapshotControlClientAutoTest {
    @Test
    void rejectsResponseLimitAboveTheAbsoluteCeilingAndNonOriginBaseUri() {
        assertThrows(IllegalArgumentException.class,
            () -> properties(URI.create("http://127.0.0.1:18106"), PlatformSnapshotProperties.MAX_RESPONSE_BYTES + 1));
        assertThrows(IllegalArgumentException.class,
            () -> properties(URI.create("http://127.0.0.1:18106/private-path"), 1_024));
        assertThrows(IllegalArgumentException.class,
            () -> properties(URI.create("http://user@127.0.0.1:18106?secret=value"), 1_024));
    }

    @Test
    void snapshotJsonDecoderRejectsExcessiveNesting() {
        JsonMapper mapper = snapshotObjectMapper(new GatewaySnapshotConfiguration());
        String nested = "{\"x\":".repeat(PlatformSnapshotProperties.MAX_JSON_NESTING_DEPTH + 1) + "0"
            + "}".repeat(PlatformSnapshotProperties.MAX_JSON_NESTING_DEPTH + 1);

        assertThrows(StreamConstraintsException.class, () -> mapper.readTree(nested));
    }

    @Test
    void acceptsAnExactHttp200SnapshotResponse() {
        PlatformSnapshotSimulator simulator = simulator();
        try {
            StepVerifier.create(client(simulator).fetch()).expectNext(GatewaySnapshotFixture.operationsSnapshot())
                .verifyComplete();
        } finally {
            simulator.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 202, 204, 206, 299, 300, 400, 404, 500})
    void rejectsEveryNon200HttpStatus(int status) {
        PlatformSnapshotSimulator simulator = simulator();
        simulator.respondWithStatus(status);
        try {
            StepVerifier.create(client(simulator).fetch()).expectError().verify();
        } finally {
            simulator.stop();
        }
    }

    /**
     * 取 Snapshot 解码器使用的 JsonMapper。
     * <li>该工厂方法为包级可见；测试位于 auto 包，经反射调用，不为测试放宽生产可见性。</li>
     *
     * @param configuration Snapshot 装配
     * @return 生产使用的 JsonMapper
     */
    private JsonMapper snapshotObjectMapper(GatewaySnapshotConfiguration configuration) {
        try {
            Method factory = GatewaySnapshotConfiguration.class.getDeclaredMethod("snapshotObjectMapper");
            factory.setAccessible(true);
            return (JsonMapper)factory.invoke(configuration);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("GatewaySnapshotConfiguration#snapshotObjectMapper 不可用", exception);
        }
    }

    private PlatformSnapshotProperties properties(URI uri, int responseBytes) {
        return new PlatformSnapshotProperties(uri, null, true, Duration.ofSeconds(1), Duration.ofSeconds(1),
            responseBytes, Duration.ofSeconds(5));
    }

    private PlatformSnapshotSimulator simulator() {
        PlatformSnapshotSimulator simulator = new PlatformSnapshotSimulator(Duration.ofSeconds(1));
        simulator.start();
        return simulator;
    }

    private PlatformGatewaySnapshotClient client(PlatformSnapshotSimulator simulator) {
        return new PlatformGatewaySnapshotClient(WebClient.builder().baseUrl(simulator.uri().toString()).build(),
            Duration.ofSeconds(1));
    }
}
