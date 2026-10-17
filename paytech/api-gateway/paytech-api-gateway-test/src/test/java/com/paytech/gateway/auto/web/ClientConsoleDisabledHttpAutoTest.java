package com.paytech.gateway.auto.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.platform.support.api.gatewayconfiguration.response.*;

/** 默认关闭的客户入口不会因为控制面存在 PUBLIC Route 而被提前开放。 */
class ClientConsoleDisabledHttpAutoTest extends GatewayAutoTestBase {
    @LocalServerPort private int port;

    @Test
    void test_clientRemainsClosedWithoutExplicitEnablement() {
        var route = new GatewayRouteSnapshot("client-register", "CLIENT", "POST", "/api/client/auth/v1/register",
            "/client-auth/v1/register", "client-console-bff", "PUBLIC", "PUBLIC", null, null, "DROP", "NONE",
            List.of("origin", "content-type"), List.of("origin", "content-type"), List.of(), 4096, 5000, 1000,
            List.of(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 10L, 10L, 60000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 2L),
                new GatewayGuardRuleSnapshot("RATE_LIMIT", "CLIENT_IP", 10L, 10L, 60000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "CLIENT_IP", null, null, null, 2L)));
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("client-console-bff", "CLIENT", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE)),
            List.of(route)));
        assertTrue(refreshSnapshot());
        GatewayPublicTestClient.builder(port, List.of("CLIENT")).defaultHeader("X-Real-IP", "203.0.113.19")
            .build().post().uri("/api/client/auth/v1/register").header("Origin", "https://client-console.test")
            .header("Content-Type", "application/json").bodyValue("{}").exchange().expectStatus().isUnauthorized()
            .expectHeader().doesNotExist("WWW-Authenticate").expectHeader().contentType("application/problem+json")
            .expectHeader().doesNotExist("Access-Control-Allow-Origin").expectBody().jsonPath("code").isEqualTo("GATEWAY_UNAUTHORIZED");
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }
}
