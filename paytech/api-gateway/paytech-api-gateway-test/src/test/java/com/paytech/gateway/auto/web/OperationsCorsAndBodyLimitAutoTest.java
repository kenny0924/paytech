package com.paytech.gateway.auto.web;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.GatewayBoundaryProbe;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.OwnerLifecycleMetadata;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore.RequestBarrier;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

/** Operations 浏览器 Origin、预检和请求体上限自动化测试。 */
@ExtendWith(OutputCaptureExtension.class)
@Import(GatewayBoundaryTestConfiguration.class)
class OperationsCorsAndBodyLimitAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGIN_PATH = OperationsTestPaths.LOGIN;
    private static final int LIMIT = 16_384;

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayBoundaryProbe boundaryProbe;

    private static Stream<Arguments> invalidOrigins() {
        return Stream.of("null", "http://operations-console.test", "https://operations-console.test.evil.test",
            "https://evil.test/https://operations-console.test", "https://operations-console.test:444",
            "https://operations-console.test, https://evil.test").map(Arguments::of);
    }

    private static Stream<Arguments> invalidPreflights() {
        return Stream.of(Arguments.of("GET", "authorization", 404, "GATEWAY_ROUTE_NOT_FOUND"),
            Arguments.of("POST", "authorization, x-extra", 403, "GATEWAY_FORBIDDEN"));
    }

    @BeforeEach
    void resetBoundaryProbe() {
        boundaryProbe.reset();
    }

    @Test
    void test_exactHttpsOriginForPostIsForwardedAndCORSHeadersAreFixed() {
        expectLogin();

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN).bodyValue("{}").exchange()
            .expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true").expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id, Retry-After").expectHeader()
            .value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                value -> Assertions.assertEquals("content-type", value.toLowerCase(java.util.Locale.ROOT)));

        Assertions.assertEquals(1, simulator().journal().count());
    }

    @ParameterizedTest
    @MethodSource("invalidOrigins")
    void test_nonExactOriginIsForbiddenWithoutBffCall(String origin) {
        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, origin).bodyValue("{}")
            .exchange(), 403, "GATEWAY_FORBIDDEN");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_missingOriginIsForbiddenWithoutBffCall() {
        assertProblem(gatewayClient().post().uri(LOGIN_PATH).bodyValue("{}").exchange(), 403,
            "GATEWAY_FORBIDDEN");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_exactPostLocalOriginExitKeepsCatalogRouteCode() {
        gatewayClient().post().uri(LOGIN_PATH).bodyValue("{}").exchange().expectStatus().isForbidden();
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));

        GatewayBoundaryTestConfiguration.Entry entry = boundaryProbe.entries().getFirst();
        Assertions.assertEquals("operations-auth-login", entry.routeCode());
        Assertions.assertEquals("AUTHORIZATION_DENIED", entry.outcome().toString());
    }

    @Test
    void test_validPreflightIsLocalAndDoesNotReachBff() {
        gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type").exchange().expectStatus().isNoContent()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN).expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true").expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST, OPTIONS").expectHeader()
            .value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                value -> Assertions.assertEquals("content-type", value.toLowerCase(java.util.Locale.ROOT)))
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id, Retry-After");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_preflightUsesMatchedRouteCorsHeaderContract() {
        assertPreflightAllowed(OperationsTestPaths.REFRESH, "content-type", "Content-Type");
        assertPreflightAllowed(OperationsTestPaths.LOGOUT, "authorization, content-type",
            "Authorization, Content-Type");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_validPreflightSetsCatalogRouteCodeAndFiniteSuccessOutcome() {
        gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange().expectStatus().isNoContent();
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));

        GatewayBoundaryTestConfiguration.Entry entry = boundaryProbe.entries().getFirst();
        Assertions.assertEquals("operations-auth-login", entry.routeCode());
        Assertions.assertEquals("UPSTREAM_RESPONSE", entry.outcome().toString());
    }

    @Test
    void test_unlistedQueryPreflightIsLocalJson400WhileEmptyQueryIsAllowed() {
        assertProblem(gatewayClient().options().uri(LOGIN_PATH + "?unexpected=1")
            .header(HttpHeaders.ORIGIN, "https://evil.test")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange(), 400,
            "GATEWAY_INVALID_REQUEST");
        Assertions.assertEquals(204, rawPreflightStatus(LOGIN_PATH + "?"));

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_invalidOperationsMethodAndPreflightPathAreJson404BeforeOriginChecks() {
        assertProblem(gatewayClient().get().uri(LOGIN_PATH).exchange(), 404, "GATEWAY_ROUTE_NOT_FOUND");
        assertProblem(gatewayClient().options().uri("/api/operations/auth/v1/unknown")
            .header(HttpHeaders.ORIGIN, "https://evil.test")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange(), 404,
            "GATEWAY_ROUTE_NOT_FOUND");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @ParameterizedTest
    @MethodSource("invalidPreflights")
    void test_invalidPreflightIsRejectedBeforeBff(String requestedMethod, String requestedHeaders, int status,
        String code) {
        assertProblem(gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeaders).exchange(), status, code);

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_duplicateOriginAndAmbiguousPreflightMethodAreRejectedWithoutBffCall() {
        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ORIGIN, ORIGIN).bodyValue("{}").exchange(), 403, "GATEWAY_FORBIDDEN");
        assertProblem(gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange(), 404,
            "GATEWAY_ROUTE_NOT_FOUND");
        Assertions.assertEquals(403, rawDuplicateRequestHeadersPreflightStatus());
        assertProblem(gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,").exchange(), 403,
            "GATEWAY_FORBIDDEN");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_physicallyPresentEmptyPreflightRequestHeadersAreForbiddenWithoutBffCall() {
        Assertions.assertEquals(403, rawRequestHeadersPreflightStatus(""));
        Assertions.assertEquals(403, rawRequestHeadersPreflightStatus(" "));

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_bodyAtExactLimitIsForwarded() {
        expectLogin();
        String body = "a".repeat(LIMIT);

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN).contentType(MediaType.TEXT_PLAIN)
            .bodyValue(body).exchange().expectStatus().isOk();

        Assertions.assertEquals(1, simulator().journal().count());
        assertValidatedFraming(LIMIT);
        assertTransportCounts(1, LIMIT);
        assertTerminalOwner(SignalType.ON_COMPLETE, LIMIT);
        awaitPooledBuffersReleased();
    }

    @Test
    void test_contentLengthAboveLimitIsRejectedWithoutBffCall() {
        String body = "a".repeat(LIMIT + 1);

        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.TEXT_PLAIN).bodyValue(body).exchange(), 413, "GATEWAY_PAYLOAD_TOO_LARGE");
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));

        Assertions.assertEquals(0, simulator().journal().count());
        assertTransportCounts(0, 0);
        Assertions.assertEquals("operations-auth-login", boundaryProbe.entries().getFirst().routeCode());
    }

    @Test
    void test_chunkedBodyAboveLimitIsRepeatedlyRejectedWithoutLeakWarning(CapturedOutput output) {
        byte[] body = "a".repeat(LIMIT + 1).getBytes(StandardCharsets.UTF_8);

        for (int attempt = 0; attempt < 2; attempt++) {
            Integer status = sendChunked(List.of(slice(body, 0, LIMIT / 2), slice(body, LIMIT / 2, body.length)));
            Assertions.assertEquals(413, status);
        }

        Assertions.assertEquals(0, simulator().journal().count());
        assertTransportCounts(0, 0);
        awaitPooledBuffersReleased();
        Assertions.assertFalse(output.getOut().contains("LEAK:"));
    }

    @Test
    void test_chunkedBodyBeyondJoinLimitReleasesPooledBuffersWithoutBffStart() {
        byte[] body = "a".repeat(LIMIT + 2).getBytes(StandardCharsets.UTF_8);

        Integer status = sendChunked(List.of(slice(body, 0, LIMIT), slice(body, LIMIT, body.length)));

        Assertions.assertEquals(413, status);
        assertTransportCounts(0, 0);
        awaitPooledBuffersReleased();
    }

    @Test
    void test_underreportedParsedContentLengthCannotBypassActualAggregation() {
        byte[] body = "a".repeat(LIMIT + 1).getBytes(StandardCharsets.UTF_8);

        Integer status = sendChunked(List.of(slice(body, 0, LIMIT / 2), slice(body, LIMIT / 2, body.length)),
            headers -> headers.set("X-Test-Parsed-Content-Length", "1"));

        Assertions.assertEquals(413, status);
        assertTransportCounts(0, 0);
        awaitPooledBuffersReleased();
    }

    @Test
    void test_chunkedBodyAtExactLimitIsForwarded() {
        expectLogin();
        byte[] body = "a".repeat(LIMIT).getBytes(StandardCharsets.UTF_8);
        Integer status = sendChunked(List.of(slice(body, 0, LIMIT / 2), slice(body, LIMIT / 2, body.length)));

        Assertions.assertEquals(200, status);
        assertValidatedFraming(LIMIT);
        assertTransportCounts(1, LIMIT);
        Assertions.assertEquals(
            simulator().journal().entries().getFirst().bodyDigestOf(new String(body, StandardCharsets.UTF_8)),
            simulator().journal().entries().getFirst().bodyDigest());
        assertTerminalOwner(SignalType.ON_COMPLETE, LIMIT);
        awaitPooledBuffersReleased();
    }

    @Test
    void test_emptyBodyIsForwardedWithEmptyDigest() {
        expectLogin();

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN).exchange().expectStatus().isOk();

        Assertions.assertEquals(simulator().journal().entries().getFirst().bodyDigestOf(""),
            simulator().journal().entries().getFirst().bodyDigest());
        assertValidatedFraming(0);
        assertTransportCounts(1, 0);
        assertTerminalOwner(SignalType.ON_COMPLETE, 0);
    }

    @Test
    void test_bodyIsReplayableAcrossTwoDownstreamSubscriptionsAndBffTransport() {
        expectLogin();
        String body = "replayable";

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header("X-Test-Replay-Subscriptions", "2").contentType(MediaType.TEXT_PLAIN).bodyValue(body).exchange()
            .expectStatus().isOk();

        Assertions.assertEquals(2, boundaryProbe.replaySubscriptionCount());
        Assertions.assertEquals(List.of(body.length(), body.length()), boundaryProbe.replayLengths());
        assertTransportCounts(1, body.length());
        assertTerminalOwner(SignalType.ON_COMPLETE, body.length());
    }

    @Test
    void test_bffTransportDoesNotStartUntilAggregationAndValidationComplete() throws Exception {
        expectLogin();
        Sinks.Many<ByteBuf> chunks = Sinks.many().unicast().onBackpressureBuffer();
        CompletableFuture<RawResponse> response = new CompletableFuture<>();
        Disposable request = chunkedClient().post().uri(LOGIN_PATH).send(chunks.asFlux())
            .responseSingle((received, content) -> content.asString().defaultIfEmpty("")
                .map(body -> new RawResponse(received.status().code(), body)))
            .subscribe(response::complete, response::completeExceptionally);

        try {
            chunks.emitNext(Unpooled.wrappedBuffer("first".getBytes(StandardCharsets.UTF_8)),
                Sinks.EmitFailureHandler.FAIL_FAST);
            boundaryProbe.awaitInboundBody(Duration.ofSeconds(1));
            Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertTransportCounts(0, 0));

            chunks.emitNext(Unpooled.wrappedBuffer("second".getBytes(StandardCharsets.UTF_8)),
                Sinks.EmitFailureHandler.FAIL_FAST);
            chunks.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            Assertions.assertEquals(200, response.get(3, TimeUnit.SECONDS).status());
            assertTransportCounts(1, 11);
        } finally {
            request.dispose();
        }
    }

    @Test
    void test_totalBodyDeadlineDoesNotResetForFrequentChunks() {
        long startedNanos = System.nanoTime();
        Flux<ByteBuf> frequentChunks = Flux.interval(Duration.ZERO, Duration.ofMillis(700))
            .map(ignored -> Unpooled.wrappedBuffer(new byte[] {1})).concatWith(Mono.never());

        RawResponse response = chunkedClient()
            .post().uri(LOGIN_PATH).send(frequentChunks).responseSingle((received, content) -> content.asString()
                .defaultIfEmpty("").map(body -> new RawResponse(received.status().code(), body)))
            .block(Duration.ofSeconds(8));

        Assertions.assertNotNull(response);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        Assertions.assertAll(() -> Assertions.assertEquals(408, response.status()),
            () -> Assertions.assertTrue(response.body().contains("GATEWAY_REQUEST_TIMEOUT")),
            () -> Assertions.assertTrue(elapsedMillis >= 4_500 && elapsedMillis < 7_500,
                () -> "请求体总截止时间未落在五秒预算附近: " + elapsedMillis + "ms"));
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));
        Assertions.assertFalse(boundaryProbe.currentOwnerMetadata().present());
        assertTransportCounts(0, 0);
        awaitPooledBuffersReleased();
    }

    @Test
    void test_downstreamDataBufferLimitExceptionIsNotReclassifiedAsPayloadTooLarge() {
        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header("X-Test-Downstream-Data-Buffer-Limit", "enabled").bodyValue("{}").exchange().expectStatus()
            .is5xxServerError().expectBody().returnResult();
        Assertions.assertNotEquals(413, result.getStatus().value());
        Assertions.assertFalse(
            new String(result.getResponseBodyContent(), StandardCharsets.UTF_8).contains("GATEWAY_PAYLOAD_TOO_LARGE"));
        Assertions.assertEquals(0, simulator().journal().count());
        assertTerminalOwner(SignalType.ON_ERROR, 2);
    }

    @Test
    void test_downstreamTimeoutExceptionIsNotReclassifiedAsBodyTimeout() {
        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header("X-Test-Downstream-Timeout", "enabled").bodyValue("{}").exchange().expectStatus().is5xxServerError()
            .expectBody().returnResult();

        Assertions.assertNotEquals(408, result.getStatus().value());
        Assertions.assertFalse(
            new String(result.getResponseBodyContent(), StandardCharsets.UTF_8).contains("GATEWAY_REQUEST_TIMEOUT"));
        assertTransportCounts(0, 0);
        assertTerminalOwner(SignalType.ON_ERROR, 2);
    }

    @Test
    void test_synchronousDownstreamConstructionFailureStillZeroesOwner() {
        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header("X-Test-Synchronous-Downstream-Fault", "enabled").bodyValue("{}").exchange().expectStatus()
            .is5xxServerError();

        assertTransportCounts(0, 0);
        assertTerminalOwner(SignalType.ON_ERROR, 2);
    }

    @Test
    void test_actualBffApplication408IsPassedThroughWithoutGatewayBodyTimeoutCode() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(408,
                "{\"code\":\"BFF_APPLICATION_TIMEOUT\",\"message\":\"timeout\",\"requestId\":\""
                    + BffScenarioStore.REQUEST_ID_TOKEN
                    + "\",\"timestamp\":\"2026-09-06T10:11:12Z\"}")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN).bodyValue("{}").exchange()
            .expectStatus().isEqualTo(408).expectBody().jsonPath("$.code").isEqualTo("BFF_APPLICATION_TIMEOUT");

        assertTransportCounts(1, 2);
        assertTerminalOwner(SignalType.ON_COMPLETE, 2);
    }

    @Test
    void test_bffResponseTimeoutZeroesOwnerOnErrorAfterAllocation() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.delayed(200, "{}", Duration.ofSeconds(2)));

        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN).bodyValue("{}")
            .exchange(), 504, "GATEWAY_UPSTREAM_TIMEOUT");

        assertTransportCounts(1, 2);
        assertTerminalOwner(SignalType.ON_ERROR, 2);
    }

    @Test
    void test_cancellingChunkedAggregationDoesNotReachBffOrEmitLeakWarning(CapturedOutput output) {
        Disposable request = chunkedClient().post().uri(LOGIN_PATH)
            .send(Flux.concat(
                reactor.core.publisher.Mono.just(Unpooled.wrappedBuffer("partial".getBytes(StandardCharsets.UTF_8))),
                reactor.core.publisher.Mono.never()))
            .responseSingle((response, content) -> content.thenReturn(response.status().code())).subscribe();

        boundaryProbe.awaitInboundBody(Duration.ofSeconds(1));
        request.dispose();
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));
        Awaitility.await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> Assertions.assertEquals(0, simulator().journal().count()));
        Assertions.assertFalse(boundaryProbe.currentOwnerMetadata().present());
        assertTransportCounts(0, 0);
        awaitPooledBuffersReleased();
        Assertions.assertFalse(output.getOut().contains("LEAK:"));
    }

    @Test
    void test_downstreamCancellationZeroesOwnerOnceAfterBodyConsumption() {
        RequestBarrier barrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}").waitingFor(barrier));
        Disposable request = chunkedClient().post().uri(LOGIN_PATH)
            .send(Flux.just(Unpooled.wrappedBuffer("{}".getBytes(StandardCharsets.UTF_8)))).response()
            .subscribe(ignored -> {
            }, ignored -> {
            });

        try {
            barrier.awaitRequest(Duration.ofSeconds(3));
            OwnerLifecycleMetadata active = boundaryProbe.currentOwnerMetadata();
            Assertions.assertAll(() -> Assertions.assertTrue(active.present()),
                () -> Assertions.assertFalse(active.zeroed()), () -> Assertions.assertEquals(0, active.clearCount()));
            request.dispose();
            boundaryProbe.awaitTerminal(Duration.ofSeconds(2));
            assertTerminalOwner(SignalType.CANCEL, 2);
        } finally {
            request.dispose();
            barrier.release();
        }
    }

    @Test
    void test_globalFilterOrderFreezesSubjectGuardBeforeBodyAndBffTimeoutBeforeSanitization() {
        Assertions.assertTrue(GatewayGlobalFilterOrder.AUTHENTICATED_GUARD < GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT);
        Assertions
            .assertTrue(GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT < GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT);
        Assertions
            .assertTrue(GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT < GatewayGlobalFilterOrder.UPSTREAM_REQUEST);
        Assertions.assertTrue(GatewayGlobalFilterOrder.UPSTREAM_REQUEST < GatewayGlobalFilterOrder.NETTY_ROUTING);
    }

    private void expectLogin() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login", BffScenarioStore.Response.json(200, "{}"));
    }

    private void assertPreflightAllowed(String path, String requestHeaders, String expectedAllowedHeaders) {
        gatewayClient().options().uri(path).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders).exchange().expectStatus().isNoContent()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST, OPTIONS").expectHeader()
            .value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                value -> Assertions.assertEquals(expectedAllowedHeaders.toLowerCase(java.util.Locale.ROOT),
                    value.toLowerCase(java.util.Locale.ROOT)));
    }

    private OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").build();
    }

    private Integer sendChunked(List<byte[]> chunks) {
        return sendChunked(chunks, ignored -> {
        });
    }

    private Integer sendChunked(List<byte[]> chunks, java.util.function.Consumer<HttpHeaders> headers) {
        return chunkedClient(headers).post().uri(LOGIN_PATH)
            .send(Flux.fromIterable(chunks).map(Unpooled::wrappedBuffer))
            .responseSingle((response, content) -> content.thenReturn(response.status().code())).block();
    }

    private HttpClient chunkedClient() {
        return chunkedClient(ignored -> {
        });
    }

    private HttpClient chunkedClient(java.util.function.Consumer<HttpHeaders> customizer) {
        return GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set("X-Real-IP", "203.0.113.17");
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            HttpHeaders mutable = new HttpHeaders();
            headers.forEach(entry -> mutable.add(entry.getKey(), entry.getValue()));
            customizer.accept(mutable);
            mutable.forEach((name, values) -> headers.set(name, values));
        });
    }

    private void assertValidatedFraming(long expectedLength) {
        Map<String, List<String>> headers = simulator().journal().entries().getFirst().headers();
        Assertions.assertEquals(Long.toString(expectedLength), firstHeader(headers, HttpHeaders.CONTENT_LENGTH));
        Assertions.assertNull(firstHeader(headers, HttpHeaders.TRANSFER_ENCODING));
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue).filter(values -> !values.isEmpty()).map(List::getFirst).findFirst().orElse(null);
    }

    private void assertTransportCounts(int expectedStarts, long expectedBodyBytes) {
        Assertions.assertEquals(expectedStarts, simulator().transportProbe().requestStartCount());
        Assertions.assertEquals(expectedBodyBytes, simulator().transportProbe().bodyByteCount());
        if (expectedBodyBytes == 0) {
            Assertions.assertEquals(0, simulator().transportProbe().bodySignalCount());
        } else {
            Assertions.assertTrue(simulator().transportProbe().bodySignalCount() > 0);
        }
    }

    private void assertTerminalOwner(SignalType terminalSignal, int length) {
        boundaryProbe.awaitTerminal(Duration.ofSeconds(2));
        OwnerLifecycleMetadata metadata = boundaryProbe.terminalOwnerMetadata();
        Assertions.assertAll(() -> Assertions.assertTrue(metadata.present()),
            () -> Assertions.assertEquals(length, metadata.length()),
            () -> Assertions.assertEquals(terminalSignal.toString(), metadata.terminalSignal()),
            () -> Assertions.assertTrue(metadata.zeroed()), () -> Assertions.assertEquals(1, metadata.clearCount()));
    }

    private void awaitPooledBuffersReleased() {
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            Assertions.assertTrue(boundaryProbe.observedPooledBufferCount() > 0, "测试未观察到真实入站 PooledDataBuffer");
            Assertions.assertTrue(boundaryProbe.allObservedPooledBuffersReleased(), "真实入站 PooledDataBuffer 在请求终止后仍被持有");
        });
    }

    private byte[] slice(byte[] source, int start, int end) {
        return java.util.Arrays.copyOfRange(source, start, end);
    }

    private Integer rawDuplicateRequestHeadersPreflightStatus() {
        String request = "OPTIONS " + LOGIN_PATH + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nOrigin: " + ORIGIN
            + "\r\n" + GatewayPublicTestClient.operationsChannelLine() + "X-Real-IP: 203.0.113.17"
            + "\r\nAccess-Control-Request-Method: POST\r\nAccess-Control-Request-Headers: authorization"
            + "\r\nAccess-Control-Request-Headers: x-extra\r\nConnection: close\r\n\r\n";
        String response = TcpClient.create().host("127.0.0.1").port(port).connect()
            .flatMap(connection -> connection.outbound().sendString(reactor.core.publisher.Mono.just(request)).then()
                .then(connection.inbound().receive().asString().next()))
            .block();
        Assertions.assertNotNull(response);
        return Integer.parseInt(response.substring(9, 12));
    }

    private Integer rawRequestHeadersPreflightStatus(String requestHeaders) {
        String request = "OPTIONS " + LOGIN_PATH + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nOrigin: " + ORIGIN
            + "\r\n" + GatewayPublicTestClient.operationsChannelLine() + "X-Real-IP: 203.0.113.17"
            + "\r\nAccess-Control-Request-Method: POST\r\nAccess-Control-Request-Headers: " + requestHeaders
            + "\r\nConnection: close\r\n\r\n";
        String response = TcpClient.create().host("127.0.0.1").port(port).connect()
            .flatMap(connection -> connection.outbound().sendString(reactor.core.publisher.Mono.just(request)).then()
                .then(connection.inbound().receive().asString().next()))
            .block();
        Assertions.assertNotNull(response);
        return Integer.parseInt(response.substring(9, 12));
    }

    private Integer rawPreflightStatus(String path) {
        String request = "OPTIONS " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nOrigin: " + ORIGIN
            + "\r\n" + GatewayPublicTestClient.operationsChannelLine() + "X-Real-IP: 203.0.113.17"
            + "\r\nAccess-Control-Request-Method: POST\r\nConnection: close\r\n\r\n";
        String response = TcpClient.create().host("127.0.0.1").port(port).connect()
            .flatMap(connection -> connection.outbound().sendString(reactor.core.publisher.Mono.just(request)).then()
                .then(connection.inbound().receive().asString().next()))
            .block();
        Assertions.assertNotNull(response);
        return Integer.parseInt(response.substring(9, 12));
    }

    private record RawResponse(int status, String body) {
    }
}
