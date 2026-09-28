package com.paytech.gateway.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

import com.paytech.gateway.routing.snapshot.PlatformGatewaySnapshotClient;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.platform.PlatformSnapshotSimulator;

import reactor.test.StepVerifier;
import tools.jackson.core.exc.StreamConstraintsException;

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
        GatewaySnapshotConfiguration configuration = new GatewaySnapshotConfiguration();
        String nested = "{\"x\":".repeat(PlatformSnapshotProperties.MAX_JSON_NESTING_DEPTH + 1) + "0"
            + "}".repeat(PlatformSnapshotProperties.MAX_JSON_NESTING_DEPTH + 1);

        assertThrows(StreamConstraintsException.class, () -> configuration.snapshotObjectMapper().readTree(nested));
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
