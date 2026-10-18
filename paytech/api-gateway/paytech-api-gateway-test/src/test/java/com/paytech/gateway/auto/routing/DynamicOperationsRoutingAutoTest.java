package com.paytech.gateway.auto.routing;

import com.paytech.gateway.test.GatewayPublicTestClient;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;

class DynamicOperationsRoutingAutoTest extends GatewayAutoTestBase {
    @LocalServerPort
    private int port;

    @Test
    void configuredGetRouteUsesExactMethodAndQueryAllowlist() {
        GatewayAutoTestRuntime.platformSnapshotSimulator()
            .publish(GatewaySnapshotFixture.operationsSnapshotWithSessionQuery(Duration.ofSeconds(1)));
        Assertions.assertTrue(refreshSnapshot());
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.GET, "/auth/v1/session",
            BffScenarioStore.Response.json(200, "{\"active\":true}"));

        client().get().uri("/api/operations/auth/v1/session?detail=summary")
            .header("Origin", "https://operations-console.test").exchange().expectStatus().isOk().expectBody()
            .json("{\"active\":true}");
        client().post().uri("/api/operations/auth/v1/session?detail=summary")
            .header("Origin", "https://operations-console.test").exchange().expectStatus().isNotFound()
            .expectHeader().valueEquals(HttpHeaders.CONNECTION, "close").expectBody()
            .jsonPath("$.code").isEqualTo("GATEWAY_ROUTE_NOT_FOUND");
        client().get().uri("/api/operations/auth/v1/session?unexpected=value")
            .header("Origin", "https://operations-console.test").exchange().expectStatus().isBadRequest().expectBody()
            .jsonPath("$.code").isEqualTo("GATEWAY_INVALID_REQUEST");

        Assertions.assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    @Test
    void pinnedOperationsChannelAppliesCorsAndRendersVariablePathOutsideLegacyPrefix() {
        GatewayAutoTestRuntime.platformSnapshotSimulator()
            .publish(GatewaySnapshotFixture.operationsSnapshotWithVariableRoute(Duration.ofSeconds(1)));
        Assertions.assertTrue(refreshSnapshot());

        client().options().uri("/edge/clients/client-1001")
            .header(HttpHeaders.ORIGIN, "https://operations-console.test")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET").exchange().expectStatus().isNoContent();
        client().get().uri("/edge/clients/client-1001").header(HttpHeaders.ORIGIN, "https://attacker.test").exchange()
            .expectStatus().isForbidden();

        // Simulator 的 request.path() 已解码，断言接收方实际拿到的单个路径变量。
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.GET,
            "/auth/clients/€value", BffScenarioStore.Response.json(200, "{\"matched\":true}"));
        // URI 重载需要完整地址；保留现有安全防火墙允许的 UTF-8 路径变量。
        client().get().uri(java.net.URI.create("http://127.0.0.1:" + port + "/edge/clients/%E2%82%ACvalue"))
            .header(HttpHeaders.ORIGIN, "https://operations-console.test").exchange().expectStatus().isOk().expectBody()
            .json("{\"matched\":true}");

        Assertions.assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    private WebTestClient client() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").responseTimeout(Duration.ofSeconds(5)).build();
    }
}
