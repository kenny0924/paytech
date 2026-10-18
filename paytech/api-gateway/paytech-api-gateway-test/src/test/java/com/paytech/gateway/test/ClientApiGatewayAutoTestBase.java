package com.paytech.gateway.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.gateway.web.GatewayProtocol;

/** Client 切片复用同一真实 Gateway、Redis、Snapshot 与两个 HTTP Simulator。 */
public abstract class ClientApiGatewayAutoTestBase extends GatewayAutoTestBase {
    @LocalServerPort
    protected int port;

    @BeforeEach
    void publishClientRoute() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(GatewaySnapshotFixture.clientApiSnapshot());
        assertTrue(refreshSnapshot());
    }

    protected WebTestClient client() {
        return GatewayPublicTestClient.clientApi(port).defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build();
    }

    protected WebTestClient.RequestBodySpec authenticatedRequest() {
        return client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL);
    }
}
