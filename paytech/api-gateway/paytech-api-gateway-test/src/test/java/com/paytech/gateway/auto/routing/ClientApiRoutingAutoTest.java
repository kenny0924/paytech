package com.paytech.gateway.auto.routing;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.GatewayBoundaryProbe;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore;
import com.paytech.gateway.web.GatewayProtocol;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

@Import(GatewayBoundaryTestConfiguration.class)
class ClientApiRoutingAutoTest extends GatewayAutoTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private GatewayBoundaryProbe boundaryProbe;

    @BeforeEach
    void resetBoundaryProbe() {
        boundaryProbe.reset();
    }

    @Test
    void clientApiOptionsDoesNotBorrowPostRouteOrEnterCorsAndGuard() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(GatewaySnapshotFixture.clientApiRoutingSnapshot());
        assertTrue(refreshSnapshot());

        assertProblem(client().options().uri("/test/client-api").header(HttpHeaders.ORIGIN, "https://operations-console.test")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
            .header(HttpHeaders.AUTHORIZATION, "Bearer test-opaque").exchange().expectStatus().isNotFound()
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)
            .expectHeader().doesNotExist(HttpHeaders.VARY), 404, "NOT_FOUND");

        assertNoGuardOrDownstream();
    }

    @ParameterizedTest
    @MethodSource("validRequestIds")
    void preservesClientApiRequestIdOnLocal404AndIp403(String requestId) {
        assertProblem(client().get().uri("/missing").header(GatewayProtocol.REQUEST_ID, requestId).exchange()
            .expectHeader().valueEquals(GatewayProtocol.REQUEST_ID, requestId), 404, "NOT_FOUND");
        assertProblem(GatewayPublicTestClient.clientApi(port).build().get().uri("/missing")
            .header(GatewayProtocol.REQUEST_ID, requestId).exchange().expectStatus().isForbidden()
            .expectHeader().valueEquals(GatewayProtocol.REQUEST_ID, requestId), 403, "FORBIDDEN");
        assertNoGuardOrDownstream();
    }

    @ParameterizedTest
    @MethodSource("fallbackRequestIds")
    void replacesMissingDuplicateAndInvalidClientApiRequestIds(List<String> values) {
        var response = client().get().uri("/missing").headers(headers -> headers.put(GatewayProtocol.REQUEST_ID, values))
            .exchange().expectStatus().isNotFound().expectBody().returnResult();
        assertProblem(response, 404, "NOT_FOUND");
        String actual = response.getResponseHeaders().getFirst(GatewayProtocol.REQUEST_ID);
        assertDoesNotThrow(() -> UUID.fromString(actual));
        assertFalse(values.contains(actual));
        assertNoGuardOrDownstream();
    }

    @Test
    void operationsAlwaysReplacesAValidExternalRequestId() {
        var response = GatewayPublicTestClient.operations(port)
            .defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build()
            .get().uri("/missing").header(GatewayProtocol.REQUEST_ID, "valid-external").exchange().expectStatus()
            .isNotFound().expectBody().returnResult();
        assertProblem(response, 404, "GATEWAY_ROUTE_NOT_FOUND");
        String actual = response.getResponseHeaders().getFirst(GatewayProtocol.REQUEST_ID);
        assertNotEquals("valid-external", actual);
        assertDoesNotThrow(() -> UUID.fromString(actual));
    }

    @Test
    void legacyOwnerErrorOnStrictClientApiTargetBecomesLocal502() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(GatewaySnapshotFixture.clientApiRoutingSnapshot());
        assertTrue(refreshSnapshot());
        String requestId = "client-request-123";
        String ownerCanary = "STRICT_CLIENT_LEGACY_CANARY";
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        String legacy = "{\"code\":\"OLD\",\"message\":\"" + ownerCanary
            + "\",\"requestId\":\"{{requestId}}\",\"timestamp\":\"2026-09-06T10:11:12Z\"}";
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH,
            ClientApiOwnerScenarioStore.Response.json(422, legacy)
                .withHeader(GatewayProtocol.REQUEST_ID, ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN)
                .withHeader("X-Owner-Canary", ownerCanary).reflectingRequestId());

        var result = client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator.CREDENTIAL)
            .header(GatewayProtocol.REQUEST_ID, requestId).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue("{}").exchange().expectStatus().isEqualTo(502).expectHeader()
            .valueEquals(GatewayProtocol.REQUEST_ID, requestId).expectHeader().doesNotExist("X-Owner-Canary")
            .expectBody().returnResult();
        assertProblem(result, 502, "UPSTREAM_PROTOCOL_ERROR");

        String body = new String(result.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(body.contains(ownerCanary));
        assertTrue(body.contains("\"requestId\":\"" + requestId + "\""));
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @ParameterizedTest
    @EnumSource(GatewayRouteChannel.class)
    void acceptsEveryExactChannelWithoutFallingBackToOperations(GatewayRouteChannel channel) {
        String path = channel == GatewayRouteChannel.OPERATIONS ? "/missing" : "/api/operations/auth/v1/login";
        assertProblem(GatewayPublicTestClient.builder(port, List.of(channel.getCode()))
            .defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build().post().uri(path).exchange()
            .expectStatus().isNotFound().expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
            .expectHeader().doesNotExist(HttpHeaders.VARY), 404,
            channel == GatewayRouteChannel.CLIENT_API ? "NOT_FOUND" : "GATEWAY_ROUTE_NOT_FOUND");
        assertNoGuardOrDownstream();
    }

    @ParameterizedTest
    @MethodSource("headerBudgets")
    void absoluteBudgetsUseTheTrustedChannelAndSumCaseInsensitiveDuplicateValues(String channel, String header,
        List<String> values, int status) {
        assertProblem(GatewayPublicTestClient.builder(port, List.of(channel))
            .defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17")
            .build().get().uri("/missing").headers(headers -> {
                headers.add(header, values.getFirst());
                if (values.size() > 1) {
                    headers.add(header.toLowerCase(java.util.Locale.ROOT), values.getLast());
                }
            }).exchange(), status, channel.equals("CLIENT_API")
                ? status == 401 ? "UNAUTHENTICATED" : status == 431 ? "REQUEST_HEADERS_TOO_LARGE" : "NOT_FOUND"
                : status == 431 ? "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE" : "GATEWAY_ROUTE_NOT_FOUND");
        assertNoGuardOrDownstream();
    }

    private static Stream<String> validRequestIds() {
        return Stream.of("Client-123._retry", "A".repeat(64));
    }

    private static Stream<List<String>> fallbackRequestIds() {
        return Stream.of(List.of(), List.of("first", "second"), List.of("same", "same"), List.of("A".repeat(65)),
            List.of("bad/value"), List.of("-leading"));
    }

    private static Stream<Arguments> headerBudgets() {
        return Stream.of("CLIENT_API", "OPERATIONS").flatMap(channel -> Stream.of(
            Arguments.of(channel, "Authorization", List.of("a".repeat(8_192)), 404),
            Arguments.of(channel, "Authorization", List.of("a".repeat(8_193)), channel.equals("CLIENT_API") ? 401 : 431),
            Arguments.of(channel, "Authorization", List.of("a".repeat(4_096), "b".repeat(4_096)), 404),
            Arguments.of(channel, "Authorization", List.of("a".repeat(4_096), "b".repeat(4_097)), channel.equals("CLIENT_API") ? 401 : 431),
            Arguments.of(channel, "Cookie", List.of("a".repeat(8_193)), 431),
            Arguments.of(channel, "Origin", List.of("a".repeat(2_049)), 431),
            Arguments.of(channel, "Access-Control-Request-Method", List.of("a".repeat(2_049)), 431),
            Arguments.of(channel, "Access-Control-Request-Headers", List.of("a".repeat(2_049)), 431)));
    }

    private WebTestClient client() {
        return GatewayPublicTestClient.clientApi(port).defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build();
    }

    private void assertNoGuardOrDownstream() {
        assertTrue(boundaryProbe.entries().isEmpty(), "请求不得进入 Route 之后的策略或 Guard 链");
        assertTrue(redisTestCleaner.guardKeys().isEmpty());
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

}
