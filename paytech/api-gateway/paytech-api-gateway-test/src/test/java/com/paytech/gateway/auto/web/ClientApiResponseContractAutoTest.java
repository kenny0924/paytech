package com.paytech.gateway.auto.web;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Response;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.gateway.web.GatewayProtocol;

/** 真实 Owner transport 的最小可信上下文、响应 Header 和 Problem 透传合同。 */
class ClientApiResponseContractAutoTest extends ClientApiGatewayAutoTestBase {
    @Test
    void test_spoofedIdentityNeverOverridesPlatformAndOnlyMinimumHeadersReachOwner() {
        expect(Response.json(200, "{\"accepted\":true}").withHeader("Set-Cookie", "must-not-leave=true")
            .withHeader("X-Internal-Test", "blocked").withHeader("Access-Control-Allow-Origin", "https://bad.test")
            .withHeader("X-Gateway-Client-Id", "blocked").withHeader("Server", "private-server"));
        GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().expectIdentity(
            PlatformCredentialValidationSimulator.CLIENT, PlatformCredentialValidationSimulator.CREDENTIAL_ID);
        var result = authenticatedRequest().header(GatewayProtocol.REQUEST_ID, "client-original-id")
            .header("X-Gateway-Client-Id", "spoof-client").header("X-Gateway-Credential-Id", "spoof-credential")
            .header("X-Gateway-Tenant-Id", "spoof-tenant").header("X-Gateway-Actor-Subject", "spoof-actor")
            .header("X-Gateway-Scope", "orders.admin").header("X-Gateway-Route-Code", "spoof-route")
            .header("X-Gateway-Channel", "OPERATIONS").header("Cookie", "test-cookie=blocked")
            .header("X-Platform-Api-Credential", "spoof-credential")
            .header("Forwarded", "for=bad").header("X-Forwarded-For", "bad").header("X-Unapproved", "blocked")
            .header("Idempotency-Key", "client-command-1").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"amount\":\"1.00\"}").exchange().expectBody().returnResult();
        assertEquals(200, result.getStatus().value());
        var entry = GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().entries().getFirst();
        assertTrue(entry.identityMatches());
        assertEquals(List.of("client-command-1"), entry.headers().get("idempotency-key"));
        assertTrue(entry.headerNames().containsAll(List.of("x-request-id", "x-gateway-client-id", "x-gateway-credential-id")));
        assertTrue(entry.headerNames().stream().noneMatch(name -> List.of("authorization", "cookie", "x-entry-channel",
            "x-gateway-tenant-id", "x-gateway-scope", "x-gateway-route-code", "x-gateway-channel", "x-real-ip",
            "forwarded", "x-forwarded-for", "reserved-gateway-header", "x-unapproved", "x-platform-api-credential").contains(name)));
        assertFalse(entry.toString().contains(PlatformCredentialValidationSimulator.CREDENTIAL));
        assertFalse(entry.toString().contains(PlatformCredentialValidationSimulator.CLIENT));
        assertEquals(17, entry.bodyLength());
        assertEquals("client-original-id", result.getResponseHeaders().getFirst(GatewayProtocol.REQUEST_ID));
        assertEquals("no-store", result.getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("no-cache", result.getResponseHeaders().getFirst(HttpHeaders.PRAGMA));
        for (String name : List.of("Set-Cookie", "X-Internal-Test", "Access-Control-Allow-Origin", "Vary",
            "X-Gateway-Client-Id", "Server")) {
            assertFalse(result.getResponseHeaders().containsHeader(name), name);
        }
    }

    @Test
    void test_fixedResponseHeadersAndCreatedLocationArePreserved() {
        expect(Response.json(201, "{}").withHeader("location", "/api/v1/checkouts/created")
            .withHeader("eTAG", "\"v1\"").withHeader("Content-Language", "en")
            .withHeader("Content-Disposition", "inline").withHeader("Last-Modified", "Sun, 06 Sep 2026 10:00:00 GMT")
            .withHeader("Retry-After", "1"));
        var result = authenticatedRequest().exchange().expectBody().returnResult();
        assertEquals(201, result.getStatus().value());
        assertEquals("/api/v1/checkouts/created", result.getResponseHeaders().getFirst(HttpHeaders.LOCATION));
        assertEquals("\"v1\"", result.getResponseHeaders().getETag());
        assertEquals("en", result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE));
        assertEquals("inline", result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("Sun, 06 Sep 2026 10:00:00 GMT", result.getResponseHeaders().getFirst(HttpHeaders.LAST_MODIFIED));
        assertEquals("1", result.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(2, result.getResponseBody().length);
    }

    @ParameterizedTest(name = "invalid created Location {index}")
    @MethodSource("invalidLocations")
    void test_invalidOrMissingCreatedLocationIs502(List<String> locations) {
        expect(Response.json(201, "{}").withHeader("Location", locations.toArray(String[]::new)));
        assertProblem(authenticatedRequest().exchange().expectBody().returnResult(), 502, "UPSTREAM_PROTOCOL_ERROR");
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    static Stream<List<String>> invalidLocations() {
        return Stream.of(List.of(), List.of("/api/v1/one", "/api/v1/two"), List.of("https://bad.test/api/v1/one"),
            List.of("//bad.test/api/v1/one"), List.of("/api/v1/one?secret=x"), List.of("/api/v1/one#fragment"),
            List.of("/api/v1/a\\b"), List.of("/internal/one"));
    }

    @Test
    void test_redirectIsNotFollowedAndLocationIsStripped() {
        expect(Response.json(302, "{}").withHeader("Location", "https://must-not-call.test/"));
        var result = authenticatedRequest().exchange().expectBody().returnResult();
        assertEquals(302, result.getStatus().value());
        assertFalse(result.getResponseHeaders().containsHeader(HttpHeaders.LOCATION));
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_validOwnerProblemKeepsStatusBodyAndOriginalRequestId() {
        String requestId = "client-owner-problem";
        String body = """
            {"type":"about:blank","title":"Conflict","detail":"Command conflict","code":"COMMAND_CONFLICT",
             "requestId":"client-owner-problem","timestamp":"2026-09-08T10:00:00.123456Z"}
            """;
        expect(Response.raw(409, body.getBytes(StandardCharsets.UTF_8), "application/problem+json")
            .withHeader(GatewayProtocol.REQUEST_ID, ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId()
            .withHeader("X-Internal-Test", "blocked"));
        var result = authenticatedRequest().header(GatewayProtocol.REQUEST_ID, requestId)
            .exchange().expectBody().returnResult();
        assertProblem(result, 409, "COMMAND_CONFLICT");
        assertArrayEquals(body.getBytes(StandardCharsets.UTF_8), result.getResponseBody());
        assertFalse(result.getResponseHeaders().containsHeader("X-Internal-Test"));
    }

    private void expect(Response response) {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, response);
    }

    @Test
    void test_encodedSuccessBodyAndTransportLengthRemainUnchanged() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(output)) {
            gzip.write("{\"accepted\":true}".getBytes(StandardCharsets.UTF_8));
        }
        byte[] encoded = output.toByteArray();
        expect(Response.raw(200, encoded, "application/json").withHeader("Content-Encoding", "gzip")
            .withDeclaredContentLength(encoded.length));
        var result = reactor.netty.http.client.HttpClient.create().compress(false).headers(headers -> {
            headers.set("X-Entry-Channel", "CLIENT_API");
            headers.set("X-Real-IP", "203.0.113.17");
            headers.set("Authorization", "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL);
        }).post().uri("http://127.0.0.1:" + port + GatewaySnapshotFixture.CLIENT_API_PATH)
            .responseSingle((response, body) -> body.asByteArray().map(bytes -> reactor.util.function.Tuples.of(
                response.status().code(), response.responseHeaders().copy(), bytes))).block();
        assertNotNull(result);
        assertEquals(200, result.getT1());
        assertEquals("gzip", result.getT2().get("Content-Encoding"));
        assertEquals(encoded.length, result.getT2().getInt("Content-Length"));
        assertArrayEquals(encoded, result.getT3());
    }
}
