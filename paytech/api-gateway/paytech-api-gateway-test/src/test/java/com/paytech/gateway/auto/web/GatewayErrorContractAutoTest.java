package com.paytech.gateway.auto.web;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.web.*;
import com.paytech.gateway.web.GatewayUpstreamFailureClassifier.Failure;

import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.internal.shaded.reactor.pool.PoolAcquirePendingLimitException;
import reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException;
import reactor.netty.internal.shaded.reactor.pool.PoolShutdownException;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Gateway 本地错误与可信请求标识自动化契约测试。 */
@ExtendWith(OutputCaptureExtension.class)
class GatewayErrorContractAutoTest extends GatewayAutoTestBase {
    private static final String ATTACKER_REQUEST_ID = "attacker-request-id";
    private static final String ATTACKER_GATEWAY_REQUEST_ID = "attacker-gateway-request-id";
    private static final String AUTHORIZATION_CANARY = "Bearer authorization-canary";
    private static final String COOKIE_CANARY = "cookie-canary";
    private static final Set<String> EXACT_PROBLEM_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");

    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayExceptionWebHandler exceptionWebHandler;

    @Autowired
    private GatewayUpstreamFailureClassifier failureClassifier;

    @Autowired
    private TrustedTerminalResponseHeaders trustedTerminalResponseHeaders;

    @Test
    void test_unmatchedRequestIsDeniedWithTrustedRequestId(CapturedOutput output) throws Exception {
        EntityExchangeResult<String> result =
            gatewayClient().get().uri("/unmatched-path").header("X-Request-Id", ATTACKER_REQUEST_ID)
                .header("X-Request-Id", ATTACKER_GATEWAY_REQUEST_ID).header("Forwarded", "for=attacker")
                .header("X-Forwarded-For", "198.51.100.1").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_CANARY)
                .header(HttpHeaders.COOKIE, "session=" + COOKIE_CANARY).exchange().expectStatus().isNotFound()
                .expectHeader().exists("X-Request-Id").expectBody(String.class).returnResult();

        String responseRequestId = result.getResponseHeaders().getFirst("X-Request-Id");
        Assertions.assertTrue(ApiProblemProfile.isValidRequestId(responseRequestId));
        assertProblem(result.getResponseHeaders(), result.getStatus(), result.getResponseBody(),
            "GATEWAY_ROUTE_NOT_FOUND");
        List<String> dates = result.getResponseHeaders().getOrEmpty(HttpHeaders.DATE);
        Assertions.assertEquals(1, dates.size());
        Assertions.assertDoesNotThrow(() -> ZonedDateTime.parse(dates.getFirst(), DateTimeFormatter.RFC_1123_DATE_TIME));
        Assertions.assertFalse(output.getOut().contains(AUTHORIZATION_CANARY));
        Assertions.assertFalse(output.getOut().contains(COOKIE_CANARY));
    }

    @Test
    void test_unrelatedTimeoutExceptionPropagatesUnchanged() {
        TimeoutException timeoutException = new TimeoutException("unrelated timeout");

        StepVerifier.create(exceptionWebHandler.handle(exchangeWithAuthorizationCanary(), timeoutException))
            .expectErrorSatisfies(actual -> Assertions.assertSame(timeoutException, actual)).verify();
    }

    @Test
    void test_gatewayResponseTimeoutWrites504WithoutSensitiveMaterial() {
        MockServerWebExchange exchange = exchangeWithAuthorizationCanary();

        exceptionWebHandler.handle(exchange, new GatewayUpstreamTimeoutException()).block();

        String responseBody = assertProblem(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_UPSTREAM_TIMEOUT");
        Assertions.assertFalse(responseBody.contains(AUTHORIZATION_CANARY));
        Assertions.assertFalse(responseBody.contains(COOKIE_CANARY));
    }

    @Test
    void test_frameworkGatewayTimeoutWritesStable504() {
        assertMapped(new org.springframework.cloud.gateway.support.TimeoutException(), HttpStatus.GATEWAY_TIMEOUT,
            "GATEWAY_UPSTREAM_TIMEOUT");
    }

    @Test
    void test_nonNotFoundResponseStatusExceptionIsNormalizedWithoutItsReason() {
        MockServerWebExchange exchange = exchangeWithAuthorizationCanary();

        exceptionWebHandler.handle(exchange,
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "response-status-secret-canary")).block();

        String body = assertProblem(exchange, HttpStatus.BAD_REQUEST, "GATEWAY_INVALID_REQUEST");
        Assertions.assertFalse(body.contains("response-status-secret-canary"));
    }

    @Test
    void test_nestedUnknownHostWritesStable503() {
        MockServerWebExchange exchange = exchangeWithAuthorizationCanary();

        exceptionWebHandler
            .handle(exchange, new IllegalStateException("wrapped", new UnknownHostException("unresolvable.invalid")))
            .block();

        assertProblem(exchange, HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void test_finiteTransportFailuresUseExactStableMappings() {
        assertMapped(new SSLException("tls detail"), HttpStatus.BAD_GATEWAY, "UPSTREAM_PROTOCOL_ERROR");
        assertMapped(PrematureCloseException.TEST_EXCEPTION, HttpStatus.BAD_GATEWAY,
            "UPSTREAM_PROTOCOL_ERROR");
        assertMapped(new SocketException("bounded socket response failure"), HttpStatus.BAD_GATEWAY,
            "UPSTREAM_PROTOCOL_ERROR");
        assertMapped(new ConnectException("loopback refused"), HttpStatus.SERVICE_UNAVAILABLE,
            "GATEWAY_DEPENDENCY_UNAVAILABLE");
        assertMapped(new PoolAcquireTimeoutException(Duration.ofMillis(1)), HttpStatus.SERVICE_UNAVAILABLE,
            "GATEWAY_DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void test_unclassifiedCheckedFailuresPropagateButUnknownRuntimeUsesSafe500() {
        List<Throwable> failures = List.of(new IOException("PrematureCloseException"),
            new TimeoutException("Gateway upstream response timeout"));

        failures.forEach(
            failure -> StepVerifier.create(exceptionWebHandler.handle(exchangeWithAuthorizationCanary(), failure))
                .expectErrorSatisfies(actual -> Assertions.assertSame(failure, actual)).verify());
        assertMapped(new RuntimeException("UnknownHostException"), HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR");
    }

    @Test
    void test_resolvedFiniteFailureTypesUseRequiredPrecedence() {
        DnsNameResolverTimeoutException dnsTimeout =
            new DnsNameResolverTimeoutException(new InetSocketAddress("127.0.0.1", 53),
                new DefaultDnsQuestion("gateway.test", DnsRecordType.A), "bounded DNS timeout");

        Assertions.assertAll(
            () -> Assertions.assertEquals(Failure.INVALID_RESPONSE,
                failureClassifier.classify(new GatewayUpstreamProtocolException()).orElseThrow()),
            () -> Assertions.assertEquals(Failure.INVALID_RESPONSE,
                failureClassifier.classify(new InvalidLineSeparatorException()).orElseThrow()),
            () -> Assertions.assertEquals(Failure.DEPENDENCY_UNAVAILABLE,
                failureClassifier.classify(dnsTimeout).orElseThrow()),
            () -> Assertions.assertEquals(Failure.DEPENDENCY_UNAVAILABLE,
                failureClassifier.classify(new PoolAcquirePendingLimitException(1)).orElseThrow()),
            () -> Assertions.assertEquals(Failure.DEPENDENCY_UNAVAILABLE,
                failureClassifier.classify(new PoolShutdownException()).orElseThrow()),
            () -> Assertions.assertEquals(Failure.UPSTREAM_TIMEOUT,
                failureClassifier.classify(ReadTimeoutException.INSTANCE).orElseThrow()),
            () -> Assertions.assertEquals(Failure.DEPENDENCY_UNAVAILABLE,
                failureClassifier.classify(new PoolAcquireTimeoutException(Duration.ofMillis(1))).orElseThrow()));
    }

    @Test
    void test_everyResolvedBffDecoderFailureIsAnInvalidResponse() {
        List<Throwable> decoderFailures =
            List.of(new ContentLengthNotAllowedException("bounded"), new InvalidChunkExtensionException(),
                new InvalidChunkTerminationException(), new TooLongHttpHeaderException(),
                new TooLongHttpLineException(), new TransferEncodingNotAllowedException("bounded"));

        decoderFailures.forEach(failure -> Assertions.assertEquals(Failure.INVALID_RESPONSE,
            failureClassifier.classify(failure).orElseThrow()));
    }

    @Test
    void test_classifierStopsAtDepthLimitAndIdentityCycle() {
        Throwable overDepth = new SSLException("unreachable tls detail");
        for (int index = 0; index < 32; index++) {
            overDepth = new RuntimeException("bounded wrapper", overDepth);
        }
        Throwable depthLimitedFailure = overDepth;
        CyclicFailure first = new CyclicFailure();
        CyclicFailure second = new CyclicFailure();
        first.cause = second;
        second.cause = first;

        Assertions.assertAll(() -> Assertions.assertTrue(failureClassifier.classify(depthLimitedFailure).isEmpty()),
            () -> Assertions.assertTrue(failureClassifier.classify(first).isEmpty()),
            () -> Assertions.assertTrue(failureClassifier.classify(new PrematureCloseLookalikeException()).isEmpty()));
    }

    @Test
    void test_terminalProtectionRejectsUnmarkedCorsAndMissingTrustedRequestId() {
        MockServerWebExchange exchange = exchangeWithAuthorizationCanary();
        // 终端策略依赖已固定的 RuntimeRoute，仅写 Route Code 已不代表完成匹配。
        var route = new com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler()
            .compile(com.paytech.gateway.test.builder.GatewaySnapshotFixture.operationsSnapshot()).catalog().all()
            .stream().filter(value -> value.routeCode().equals("operations-auth-login")).findFirst().orElseThrow();
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getResponse().getHeaders().add("X-Request-Id", ATTACKER_GATEWAY_REQUEST_ID);
        exchange.getResponse().getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://attacker.test");
        exchange.getResponse().getHeaders().add(HttpHeaders.CACHE_CONTROL, "public");
        exchange.getResponse().getHeaders().add(HttpHeaders.PRAGMA, "public");
        exchange.getResponse().getHeaders().add(HttpHeaders.VARY, "Accept-Encoding, Origin");
        exchange.getResponse().getHeaders().add(HttpHeaders.SET_COOKIE, "session=preserved");
        exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin-Extra", "near-cors");
        exchange.getResponse().getHeaders().add("Cache-Control-Extra", "near-cache");
        exchange.getResponse().getHeaders().add("X-Request-Id-Extra", "near-request-id");

        trustedTerminalResponseHeaders.protect(exchange);

        Assertions.assertAll(
            () -> Assertions.assertTrue(exchange.getResponse().getHeaders().getOrEmpty("X-Request-Id").isEmpty()),
            () -> Assertions.assertTrue(
                exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(List.of("no-store"),
                exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of("no-cache"),
                exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.PRAGMA)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN),
                exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertEquals(List.of("session=preserved"),
                exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)),
            () -> Assertions.assertEquals(List.of("near-cors"),
                exchange.getResponse().getHeaders().getOrEmpty("Access-Control-Allow-Origin-Extra")),
            () -> Assertions.assertEquals(List.of("near-cache"),
                exchange.getResponse().getHeaders().getOrEmpty("Cache-Control-Extra")),
            () -> Assertions.assertEquals(List.of("near-request-id"),
                exchange.getResponse().getHeaders().getOrEmpty("X-Request-Id-Extra")));
    }

    /** 框架状态异常也遵守既有 Problem 合同，不能泄露异常 reason。 */
    @ParameterizedTest
    @CsvSource({"401,GATEWAY_UNAUTHORIZED", "403,GATEWAY_FORBIDDEN", "404,GATEWAY_ROUTE_NOT_FOUND",
        "408,GATEWAY_REQUEST_TIMEOUT", "413,GATEWAY_PAYLOAD_TOO_LARGE", "431,GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE",
        "502,UPSTREAM_PROTOCOL_ERROR", "503,GATEWAY_DEPENDENCY_UNAVAILABLE", "504,GATEWAY_UPSTREAM_TIMEOUT",
        "422,GATEWAY_INVALID_REQUEST", "500,INTERNAL_ERROR"})
    void test_frameworkStatusFailuresPreservePublicErrorContract(int status, String code) {
        var exchange = exchangeWithAuthorizationCanary();
        exceptionWebHandler.handle(exchange, new ResponseStatusException(HttpStatus.valueOf(status),
            "private-exception-reason")).block();
        String body = assertProblem(exchange, HttpStatus.valueOf(status), code);
        Assertions.assertFalse(body.contains("private-exception-reason"));
    }

    /** 客户端取消和传输中断不伪造新的错误响应；有环或过深 cause 必须有界结束。 */
    @Test
    void test_transportAbortsAndUnrepresentableStatusesPropagateUnchanged() {
        for (Throwable failure : List.of(new java.util.concurrent.CancellationException(), reactor.core.Exceptions.failWithCancel(),
            new IOException("Broken pipe"), new IOException("Connection reset by peer"), new IOException(),
            new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(599)),
            new ResponseStatusException(HttpStatus.ACCEPTED))) {
            var exchange = exchangeWithAuthorizationCanary();
            StepVerifier.create(exceptionWebHandler.handle(exchange, failure))
                .expectErrorSatisfies(actual -> Assertions.assertSame(failure, actual)).verify();
            Assertions.assertNull(exchange.getResponse().getStatusCode());
        }
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);
        assertMapped(first, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        RuntimeException nested = new RuntimeException("end");
        for (int index = 0; index < 40; index++) nested = new RuntimeException("wrapper", nested);
        assertMapped(nested, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").build();
    }

    private MockServerWebExchange exchangeWithAuthorizationCanary() {
        return MockServerWebExchange
            .from(MockServerHttpRequest.get("/gateway-timeout").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_CANARY)
                .header(HttpHeaders.COOKIE, "session=" + COOKIE_CANARY));
    }

    private void assertMapped(Throwable failure, HttpStatus status, String code) {
        MockServerWebExchange exchange = exchangeWithAuthorizationCanary();
        exceptionWebHandler.handle(exchange, failure).block();
        assertProblem(exchange, status, code);
    }

    private String assertProblem(MockServerWebExchange exchange, HttpStatus status, String code) {
        String body = exchange.getResponse().getBodyAsString().block();
        assertProblem(exchange.getResponse().getHeaders(), exchange.getResponse().getStatusCode(), body, code);
        Assertions.assertEquals(status, exchange.getResponse().getStatusCode());
        return body;
    }

    private void assertProblem(HttpHeaders headers, org.springframework.http.HttpStatusCode actualStatus, String body,
        String code) {
        Assertions.assertEquals(List.of(ApiProblemProfile.CONTENT_TYPE),
            headers.getOrEmpty(HttpHeaders.CONTENT_TYPE));
        Assertions.assertTrue(headers.getOrEmpty("X-Timestamp").isEmpty());
        List<String> requestIds = headers.getOrEmpty(ApiProblemProfile.REQUEST_ID_HEADER);
        Assertions.assertEquals(1, requestIds.size());
        String requestId = requestIds.getFirst();
        Assertions.assertTrue(ApiProblemProfile.isValidRequestId(requestId));
        JsonNode problem = json.readTree(body);
        Assertions.assertEquals(EXACT_PROBLEM_MEMBERS, problem.propertyNames());
        EXACT_PROBLEM_MEMBERS.forEach(member -> {
            Assertions.assertTrue(problem.get(member).isString(), member);
            Assertions.assertFalse(problem.get(member).stringValue().isBlank(), member);
        });
        Assertions.assertEquals(ApiProblemProfile.TYPE, problem.get("type").stringValue());
        HttpStatus status = HttpStatus.valueOf(actualStatus.value());
        Assertions.assertEquals(status.getReasonPhrase(), problem.get("title").stringValue());
        Assertions.assertEquals(code, problem.get("code").stringValue());
        Assertions.assertEquals(requestId, problem.get("requestId").stringValue());
        Assertions.assertTrue(ApiProblemProfile.isCanonicalTimestamp(problem.get("timestamp").stringValue()));
    }

    private static final class CyclicFailure extends RuntimeException {
        private Throwable cause;

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }

    private static final class PrematureCloseLookalikeException extends RuntimeException {}
}
