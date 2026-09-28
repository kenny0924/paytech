package com.paytech.foundation.httpclient.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.httpclient.test.simulator.HttpServiceSimulator;

/** Verifies the typed client through a real Loopback HTTP server. @author Codex */
public class HttpServiceClientFactoryTest {

    private static final String PRIVATE_DETAIL = "private-password-must-not-leak";
    private static final String REQUEST_ID = "upstream-request-01";

    private HttpServiceSimulator simulator;
    private ResourceApi api;

    @BeforeEach
    void setUp() throws IOException {
        simulator = new HttpServiceSimulator();
        api = HttpServiceClientFactory.create(client(Duration.ofSeconds(2)), ContractError.values())
            .createClient(ResourceApi.class);
    }

    @AfterEach
    void tearDown() {
        simulator.close();
    }

    @Test
    void test_importedHttpServiceUsesTheSharedErrorBoundary() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean("transport", org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer.class,
                () -> groups -> groups.forEachClient((group, builder) -> builder.baseUrl(simulator.baseUrl())));
            context.registerBean("errors", org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer.class,
                () -> HttpServiceClientFactory.configureGroup("resource-service", HttpServiceClientOptions.strict(),
                    ContractError.values()));
            context.register(ImportedResourceConfiguration.class);
            context.refresh();
            var imported = context.getBean(ResourceApi.class);
            try (var ignored = org.slf4j.MDC.putCloseable("requestId", "group-request-A")) {
                assertEquals(0, imported.read().version());
            }
            assertEquals(List.of("group-request-A"), simulator.requestHeaders().getFirst().get("X-Request-Id"));
            assertSame(imported, context.getBean(ResourceApi.class));
            respondProblem(401, problem(401, "SESSION_EXPIRED"));
            assertSame(ContractError.SESSION_EXPIRED, assertThrows(FailException.class, imported::read).getErrorCode());
            respondProblem(503, problem(503, "SERVICE_UNAVAILABLE"));
            assertThrows(ServiceUnavailableException.class, imported::read);
            simulator.respond(302, "{}", "application/json");
            assertInvalidResponse(imported::read);
            assertEquals(4, simulator.requests().size());
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.web.service.registry.ImportHttpServices(group = "resource-service", types = ResourceApi.class)
    static class ImportedResourceConfiguration {}

    @Test
    void test_currentRequestIdIsPropagatedPerCallWithoutCopyingOtherMdcOrLeakingBetweenRequests() {
        try (var ignored = org.slf4j.MDC.putCloseable("requestId", "request-A");
            var privateValue = org.slf4j.MDC.putCloseable("accessToken", "private-test-marker")) {
            api.read();
            assertEquals("request-A", org.slf4j.MDC.get("requestId"));
        }
        try (var ignored = org.slf4j.MDC.putCloseable("requestId", "request-B")) {
            api.read();
        }
        api.read();
        try (var ignored = org.slf4j.MDC.putCloseable("requestId", "bad request id")) {
            api.read();
        }
        assertEquals(List.of("request-A"), simulator.requestHeaders().get(0).get("X-Request-Id"));
        assertEquals(List.of("request-B"), simulator.requestHeaders().get(1).get("X-Request-Id"));
        assertNull(simulator.requestHeaders().get(2).get("X-Request-Id"));
        assertNull(simulator.requestHeaders().get(3).get("X-Request-Id"));
        assertFalse(simulator.requestHeaders().stream().anyMatch(headers -> headers.containsKey("accessToken")));
    }

    @Test
    void test_strictJsonResponsesUseTypedBindingAndRejectMissingNullOrCoercedFields() {
        var strictApi = HttpServiceClientFactory.create(client(Duration.ofSeconds(2)),
            HttpServiceClientOptions.strict().withStrictJsonResponses(1024)).createClient(StrictResourceApi.class);
        simulator.respond(200, "{\"available\":true,\"note\":null}", "application/json");
        assertEquals(new StrictResourceResponse(true, null), strictApi.read());
        for (String body : List.of("{}", "{\"available\":true}",
            "{\"available\":null,\"note\":null}", "{\"available\":\"true\",\"note\":null}",
            "{\"available\":true,\"note\":42}", "{\"available\":true,\"note\":false}",
            "{\"available\":true,\"note\":null,\"extra\":1}",
            "{\"available\":true,\"available\":false,\"note\":null}",
            "{\"available\":true,\"note\":null} {}")) {
            simulator.respond(200, body, "application/json");
            assertInvalidResponse(strictApi::read);
        }
    }

    @Test
    void test_strictJsonResponseLimitCountsUtf8BytesAndAppliesToErrorBodies() {
        var strictApi = HttpServiceClientFactory.create(client(Duration.ofSeconds(2)),
            HttpServiceClientOptions.strict().withStrictJsonResponses(64)).createClient(StrictResourceApi.class);
        String body = "{\"available\":true,\"note\":\"中文\"}";
        body += " ".repeat(64 - body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        simulator.respond(200, body, "application/json");
        assertEquals("中文", strictApi.read().note());
        simulator.respond(200, body + " ", "application/json");
        assertInvalidResponse(strictApi::read);
        respondProblem(503, problem(503, "SERVICE_UNAVAILABLE"));
        assertInvalidResponse(strictApi::read);
    }

    @HttpExchange("/resource")
    public interface StrictResourceApi {
        @GetExchange
        StrictResourceResponse read();
    }

    public record StrictResourceResponse(boolean available, String note) {}

    @Test
    void test_normalFlowRetainsTypedBodiesHeadersAndVoidCommands() {
        assertEquals(0, api.read().version());
        api.update();
        assertNull(api.updateBoxed());
        assertEquals(HttpStatus.NO_CONTENT, api.updateEntity().getStatusCode());
        assertEquals(3, api.readEntity().getBody().version());
        assertEquals("3", api.headers().getFirst("X-Service-Version"));
        assertEquals(3, simulator.version());
        assertEquals(6, simulator.requests().size());
    }

    @ParameterizedTest
    @CsvSource({"400,BUSINESS_CHECK_FAILED", "400,INVALID_REQUEST", "409,CONCURRENT_REQUEST", "404,NOT_FOUND"})
    void test_registeredCommon4xxProblemMapsToItsLocalException(int status, String code) {
        respondProblem(status, problem(status, code));

        FailException failure = assertThrows(FailException.class, api::read);

        assertEquals(code, failure.getCode());
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
        Class<?> expected = switch (code) {
            case "BUSINESS_CHECK_FAILED" -> BizCheckFailException.class;
            case "INVALID_REQUEST" -> InvalidParameterException.class;
            case "NOT_FOUND" -> FailException.class;
            default -> ConcurrentRequestException.class;
        };
        assertEquals(expected, failure.getClass());
    }

    @Test
    void test_explicitBodyValidationPreservesOnlyPathsAndOtherClientsKeepTheirContract() {
        respondProblem(422, problem(422, "VALIDATION_ERROR").replace("bad", PRIVATE_DETAIL));
        var options = HttpServiceClientOptions.strict().withRequestValidationFailures()
            .withDependencyFailureMapper((kind, cause) -> new ServiceUnavailableException(cause))
            .withStrictJsonResponses(65536);
        var enabled = HttpServiceClientFactory.create(client(Duration.ofSeconds(2)), options)
            .createClient(ValidationApi.class);
        var failure = assertThrows(HttpRequestValidationException.class, () -> enabled.save(new ValidationBody("a")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR.getMessage(), failure.getMessage());
        assertEquals(List.of("name"), failure.fieldPaths());
        assertThrows(UnsupportedOperationException.class, () -> failure.fieldPaths().add("changed"));
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
        assertInvalidResponse(api::read);
        var defaultBody = HttpServiceClientFactory.create(client(Duration.ofSeconds(2))).createClient(ValidationApi.class);
        assertInvalidResponse(() -> defaultBody.save(new ValidationBody("a")));
        var noBody = HttpServiceClientFactory.create(client(Duration.ofSeconds(2)), options).createClient(ResourceApi.class);
        assertInvalidResponse(noBody::read);
    }

    @Test
    void test_bodyValidationCanOmitErrorsAndStillRejectsMalformedProblemOrWrongStatus() {
        var enabled = HttpServiceClientFactory.create(client(Duration.ofSeconds(2)),
            HttpServiceClientOptions.migrationWithLegacyErrorResponse().withRequestValidationFailures())
            .createClient(ValidationApi.class);
        String body = problem(422, "VALIDATION_ERROR");
        respondProblem(422, body.substring(0, body.indexOf(",\"errors\"")) + "}");
        assertEquals(List.of(), assertThrows(HttpRequestValidationException.class,
            () -> enabled.save(new ValidationBody("a"))).fieldPaths());
        for (String malformed : List.of(body.replace("Unprocessable Content", "Bad Request"),
            body.replace(REQUEST_ID, "wrong-id"), body.replace(".123456Z", "Z"),
            body.replace("\"message\":\"bad\"", "\"message\":null"))) {
            respondProblem(422, malformed);
            assertInvalidResponse(() -> enabled.save(new ValidationBody("a")));
        }
        respondProblem(400, problem(400, "VALIDATION_ERROR"));
        assertInvalidResponse(() -> enabled.save(new ValidationBody("a")));
        simulator.respond(422, legacy("VALIDATION_ERROR"), "application/json");
        assertInvalidResponse(() -> enabled.save(new ValidationBody("a")));
    }

    @HttpExchange("/resource")
    public interface ValidationApi {
        @PostExchange
        void save(@org.springframework.web.bind.annotation.RequestBody ValidationBody body);
    }

    public record ValidationBody(String name) { }

    @Test
    void test_validProblemAllowsUnknownTopLevelExtensions() {
        respondProblem(401, problem(401, "SESSION_EXPIRED")
            .replace("}]}", "}],\"diagnosticReference\":\"safe-extension\"}"));

        FailException failure = assertThrows(FailException.class, api::read);

        assertSame(ContractError.SESSION_EXPIRED, failure.getErrorCode());
        assertEquals(ContractError.SESSION_EXPIRED.getMessage(), failure.getMessage());
    }

    @Test
    void test_legacyErrorResponseIsRejectedByTheStrictDefault() {
        simulator.respond(400, legacy("BUSINESS_CHECK_FAILED"), "application/json");

        assertInvalidResponse(api::read);
    }

    @Test
    void test_explicitMigrationOptionAcceptsOnlyLegacyApplicationJson() {
        ResourceApi migrationApi = HttpServiceClientFactory.create(
            client(Duration.ofSeconds(2)), HttpServiceClientOptions.migrationWithLegacyErrorResponse(), ContractError.values()
        ).createClient(ResourceApi.class);
        simulator.respond(400, legacy("BUSINESS_CHECK_FAILED"), "application/json");

        BizCheckFailException failure = assertThrows(BizCheckFailException.class, migrationApi::read);

        assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getMessage(), failure.getMessage());
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
        simulator.respond(400, legacy("BUSINESS_CHECK_FAILED"), "text/plain");
        assertInvalidResponse(migrationApi::read);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "{}", "null",
        "{\"type\":\"about:blank\",\"type\":\"about:blank\"}",
        "{\"type\":\"about:blank\"} trailing"
    })
    void test_malformedOrEmptyProblemBodyFailsClosed(String body) {
        respondProblem(400, body);

        assertInvalidResponse(api::read);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/plain", "application/problem+xml"})
    void test_wrongProblemMediaTypeFailsClosed(String contentType) {
        simulator.respond(400, problem(400, "BUSINESS_CHECK_FAILED"), contentType, requestHeaders());

        assertInvalidResponse(api::read);
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "title", "detail", "code", "requestId", "timestamp"})
    void test_missingRequiredProblemMemberFailsClosed(String field) {
        String body = problem(400, "BUSINESS_CHECK_FAILED")
            .replaceAll("\\\"" + field + "\\\":\\\"[^\\\"]*\\\",?", "");
        respondProblem(400, body);

        assertInvalidResponse(api::read);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "instance", "message"})
    void test_forbiddenProblemMemberFailsClosed(String forbidden) {
        respondProblem(400, problem(400, "BUSINESS_CHECK_FAILED")
            .replace("}]}", "}],\"" + forbidden + "\":\"forbidden\"}"));

        assertInvalidResponse(api::read);
    }

    @Test
    void test_wrongTypeTitleTimestampHeaderParityAndFieldErrorShapeFailClosed() {
        List<String> invalidBodies = List.of(
            problem(400, "BUSINESS_CHECK_FAILED").replace("about:blank", "urn:wrong"),
            problem(400, "BUSINESS_CHECK_FAILED").replace("Bad Request", "Conflict"),
            problem(400, "BUSINESS_CHECK_FAILED").replace(".123456Z", "Z"),
            problem(400, "BUSINESS_CHECK_FAILED").replace("\"errors\":[{\"field\":\"name\",\"message\":\"bad\"}]", "\"errors\":[{\"field\":\"name\",\"message\":\"bad\",\"code\":\"not-allowed\"}]")
        );
        for (String body : invalidBodies) {
            respondProblem(400, body);
            assertInvalidResponse(api::read);
        }
        simulator.respond(400, problem(400, "BUSINESS_CHECK_FAILED"), "application/problem+json",
            Map.of("X-Request-Id", "other-request"));
        assertInvalidResponse(api::read);
    }

    @Test
    void test_duplicateOrInvalidRequestIdHeaderFailsClosed() {
        simulator.respond(400, problem(400, "BUSINESS_CHECK_FAILED"), "application/problem+json",
            Map.of("X-Request-Id", List.of(REQUEST_ID, "second-request")));
        assertInvalidResponse(api::read);
        simulator.respond(400, problem(400, "BUSINESS_CHECK_FAILED"), "application/problem+json",
            Map.of("X-Request-Id", "invalid request id"));
        assertInvalidResponse(api::read);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void test_validServerProblemMapsToUnavailableAfterWireValidation(int status) {
        respondProblem(status, problem(status, "SERVICE_UNAVAILABLE"));

        ServiceUnavailableException failure = assertThrows(ServiceUnavailableException.class, api::read);

        assertInstanceOf(RestClientResponseException.class, failure.getCause());
    }

    @Test
    void test_malformedServerProblemFailsClosedInsteadOfBeingTrustedAsUnavailable() {
        respondProblem(503, PRIVATE_DETAIL + "{");

        ErrorException failure = assertInvalidResponse(api::read);

        RestClientResponseException cause = assertInstanceOf(RestClientResponseException.class, failure.getCause());
        assertEquals(PRIVATE_DETAIL + "{", cause.getResponseBodyAsString());
        assertNoRemoteBodyOrParserDiagnostic(failure);
    }

    @Test
    void test_originalResponseExceptionIdentityAndConcreteSubtypeAreRetained() {
        String body = problem(400, "BUSINESS_CHECK_FAILED").replace("safe-detail", PRIVATE_DETAIL);
        respondProblem(400, body);

        BizCheckFailException failure = assertThrows(BizCheckFailException.class, api::read);
        RestClientResponseException cause = assertInstanceOf(RestClientResponseException.class, failure.getCause());

        assertSame(cause, failure.getCause());
        assertInstanceOf(HttpClientErrorException.BadRequest.class, cause);
        assertEquals(body, cause.getResponseBodyAsString());
        assertNull(cause.getCause());
        assertNoRemoteBodyOrParserDiagnostic(failure);
    }

    @Test
    void test_originalServerResponseExceptionRetainsBodyWithSanitizedThrowableMessage() {
        String body = problem(503, "SERVICE_UNAVAILABLE").replace("safe-detail", PRIVATE_DETAIL);
        respondProblem(503, body);

        ServiceUnavailableException failure = assertThrows(ServiceUnavailableException.class, api::read);
        RestClientResponseException cause = assertInstanceOf(RestClientResponseException.class, failure.getCause());

        assertInstanceOf(org.springframework.web.client.HttpServerErrorException.ServiceUnavailable.class, cause);
        assertEquals(body, cause.getResponseBodyAsString());
        assertNoRemoteBodyOrParserDiagnostic(failure);
    }

    @Test
    void test_explicitErrorsNullFailsClosedWhileAbsentErrorsAreAccepted() {
        respondProblem(400, problem(400, "BUSINESS_CHECK_FAILED")
            .replace(",\"errors\":[{\"field\":\"name\",\"message\":\"bad\"}]", ",\"errors\":null"));
        assertInvalidResponse(api::read);
        respondProblem(400, problem(400, "BUSINESS_CHECK_FAILED")
            .replace(",\"errors\":[{\"field\":\"name\",\"message\":\"bad\"}]", ""));
        assertInstanceOf(BizCheckFailException.class, assertThrows(FailException.class, api::read));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"errors\":[]", "\"errors\":{}", "\"errors\":[null]",
        "\"errors\":[{\"field\":\"name\"}]", "\"errors\":[{\"message\":\"bad\"}]",
        "\"errors\":[{\"field\":\" \",\"message\":\"bad\"}]", "\"errors\":[{\"field\":\"name\",\"message\":\" \"}]",
        "\"errors\":[{\"field\":\"name\",\"message\":\"bad\",\"extra\":\"x\"}]"
    })
    void test_invalidFieldErrorShapesFailClosed(String errors) {
        String body = problem(400, "BUSINESS_CHECK_FAILED")
            .replace("\"errors\":[{\"field\":\"name\",\"message\":\"bad\"}]", errors);
        respondProblem(400, body);

        assertInvalidResponse(api::read);
    }

    @Test
    void test_completeDuplicateAndTrailingProblemJsonFailClosed() {
        respondProblem(400, problem(400, "BUSINESS_CHECK_FAILED")
            .replace("\"detail\":\"safe-detail\"", "\"detail\":\"safe-detail\",\"detail\":\"safe-detail\""));
        assertInvalidResponse(api::read);
        respondProblem(400, problem(400, "BUSINESS_CHECK_FAILED") + " trailing");
        assertInvalidResponse(api::read);
    }

    @Test
    void test_completeUnknownProblemCodeFailsClosed() {
        respondProblem(400, problem(400, "UNKNOWN_SAFE_LOOKING_CODE"));

        assertInvalidResponse(api::read);
    }

    @Test
    void test_legacyReaderAcceptsBoundaryRequestIds() {
        for (String requestId : List.of("a", "a".repeat(128), "legacy:request-id")) {
            ResourceApi migrationApi = migrationApi();
            simulator.respond(400, legacy("BUSINESS_CHECK_FAILED", requestId), "application/json");

            assertInstanceOf(BizCheckFailException.class, assertThrows(FailException.class, migrationApi::read));
        }
    }

    @Test
    void test_legacyReaderRejectsInvalidRequestIds() {
        for (String requestId : List.of("", "invalid request", "invalid!", "a".repeat(129))) {
            ResourceApi migrationApi = migrationApi();
            simulator.respond(400, legacy("BUSINESS_CHECK_FAILED", requestId), "application/json");

            assertInvalidResponse(migrationApi::read);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-09-04T10:00:00Z",
        "2026-09-04T10:00:00.1Z",
        "2026-09-04T10:00:00.123Z",
        "2026-09-04T10:00:00.123456Z",
        "2026-09-04T10:00:00.123456789Z"
    })
    void test_legacyReaderAcceptsApprovedUtcTimestampFractions(String timestamp) {
        ResourceApi migrationApi = migrationApi();
        simulator.respond(400, legacy("BUSINESS_CHECK_FAILED", "legacy-request", timestamp), "application/json");

        assertInstanceOf(BizCheckFailException.class, assertThrows(FailException.class, migrationApi::read));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-09-04T10:00:00+00:00",
        "2026-09-04T10:00:00z",
        "+12026-09-04T10:00:00Z",
        "2026-09-04T10:00:60Z",
        "2026-02-30T10:00:00Z",
        "2026-09-04T24:00:00Z",
        "2026-09-04T10:00:00.1234567890Z"
    })
    void test_legacyReaderRejectsNonCanonicalUtcTimestamps(String timestamp) {
        ResourceApi migrationApi = migrationApi();
        simulator.respond(400, legacy("BUSINESS_CHECK_FAILED", "legacy-request", timestamp), "application/json");

        assertInvalidResponse(migrationApi::read);
    }

    @Test
    void test_legacyReaderRejectsMissingNullBlankWrongTypeAndExtraMembers() {
        ResourceApi migrationApi = migrationApi();
        List<String> invalidBodies = List.of(
            legacy("BUSINESS_CHECK_FAILED").replace("\"code\":\"BUSINESS_CHECK_FAILED\",", ""),
            legacy("BUSINESS_CHECK_FAILED").replace("\"message\":\"legacy-safe-detail\"", "\"message\":null"),
            legacy("BUSINESS_CHECK_FAILED").replace("\"requestId\":\"legacy-request\"", "\"requestId\":\" \""),
            legacy("BUSINESS_CHECK_FAILED").replace("\"timestamp\":\"2026-09-04T10:00:00Z\"", "\"timestamp\":123"),
            legacy("BUSINESS_CHECK_FAILED").replace("}", ",\"extra\":\"not-allowed\"}")
        );
        for (String body : invalidBodies) {
            simulator.respond(400, body, "application/json");

            assertInvalidResponse(migrationApi::read);
        }
    }

    @Test
    void test_non2xxHeadValidatesOnlyMediaTypeAndSingleRequestIdWithoutBody() {
        simulator.respond(404, "", "application/problem+json", requestHeaders());

        BizCheckFailException failure = assertThrows(BizCheckFailException.class, api::head);

        assertInstanceOf(RestClientResponseException.class, failure.getCause());
        assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), failure.getCode());
        assertEquals(List.of("HEAD /resource"), simulator.requests());
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_REQUEST", "409,CONCURRENT_REQUEST"})
    void test_non2xxHeadMapsStatusesWithExistingSafeCategories(int status, String expectedCode) {
        simulator.respond(status, "", "application/problem+json", requestHeaders());

        FailException failure = assertThrows(FailException.class, api::head);

        assertEquals(expectedCode, failure.getCode());
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
    }

    @Test
    void test_non2xxHeadMapsValidServerStatusToUnavailable() {
        simulator.respond(503, "", "application/problem+json", requestHeaders());

        ServiceUnavailableException failure = assertThrows(ServiceUnavailableException.class, api::head);

        assertInstanceOf(RestClientResponseException.class, failure.getCause());
    }

    @Test
    void test_compliant504InvokesTimeoutMapperOnceAndRetainsTheResponseCause() {
        AtomicInteger invocationCount = new AtomicInteger();
        MapperInvocation[] invocation = new MapperInvocation[1];
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            invocation[0] = new MapperInvocation(kind, cause);
            return new TargetDependencyFailure(kind, cause);
        });
        String body = problem(504, "SERVICE_UNAVAILABLE").replace("safe-detail", PRIVATE_DETAIL);
        respondProblem(504, body);

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.TIMEOUT, invocation[0].kind());
        assertSame(invocation[0].cause(), failure.getCause());
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
        assertEquals(body, ((RestClientResponseException) failure.getCause()).getResponseBodyAsString());
        assertNoRemoteBodyOrParserDiagnostic(failure);
    }

    @Test
    void test_malformed504FailsClosedWithoutInvokingTheDependencyMapper() {
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            return new TargetDependencyFailure(kind, cause);
        });
        respondProblem(504, PRIVATE_DETAIL + "{");

        ErrorException failure = assertInvalidResponse(mappedApi::read);

        assertEquals(0, invocationCount.get());
        assertNoRemoteBodyOrParserDiagnostic(failure);
    }

    @Test
    void test_compliantHead504InvokesTimeoutMapperOnceAfterHeaderValidation() {
        AtomicInteger invocationCount = new AtomicInteger();
        MapperInvocation[] invocation = new MapperInvocation[1];
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            invocation[0] = new MapperInvocation(kind, cause);
            return new TargetDependencyFailure(kind, cause);
        });
        simulator.respond(504, "", "application/problem+json", requestHeaders());

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::head);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.TIMEOUT, invocation[0].kind());
        assertSame(invocation[0].cause(), failure.getCause());
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
    }

    @Test
    void test_readTimeoutInvokesTimeoutMapperOnceAndRetainsTheTransportCause() {
        simulator.delayResponse();
        AtomicInteger invocationCount = new AtomicInteger();
        MapperInvocation[] invocation = new MapperInvocation[1];
        ResourceApi mappedApi = mappedApi(Duration.ofMillis(100), (kind, cause) -> {
            invocationCount.incrementAndGet();
            invocation[0] = new MapperInvocation(kind, cause);
            return new TargetDependencyFailure(kind, cause);
        });

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.TIMEOUT, invocation[0].kind());
        assertSame(invocation[0].cause(), failure.getCause());
        assertInstanceOf(ResourceAccessException.class, failure.getCause());
        assertEquals(1, simulator.requests().size());
    }

    @Test
    void test_connectionFailureInvokesUnavailableMapperOnceAndRetainsTheTransportCause() throws IOException {
        simulator.close();
        AtomicInteger invocationCount = new AtomicInteger();
        MapperInvocation[] invocation = new MapperInvocation[1];
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            invocation[0] = new MapperInvocation(kind, cause);
            return new TargetDependencyFailure(kind, cause);
        });

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.UNAVAILABLE, invocation[0].kind());
        assertSame(invocation[0].cause(), failure.getCause());
        assertInstanceOf(ResourceAccessException.class, failure.getCause());
    }

    @Test
    void test_ordinary5xxInvokesUnavailableMapperOnceAndRetainsTheResponseCause() {
        AtomicInteger invocationCount = new AtomicInteger();
        MapperInvocation[] invocation = new MapperInvocation[1];
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            invocation[0] = new MapperInvocation(kind, cause);
            return new TargetDependencyFailure(kind, cause);
        });
        respondProblem(503, problem(503, "SERVICE_UNAVAILABLE"));

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.UNAVAILABLE, invocation[0].kind());
        assertSame(invocation[0].cause(), failure.getCause());
        assertInstanceOf(RestClientResponseException.class, failure.getCause());
    }

    @Test
    void test_nullDependencyMapperResultFailsClosedAfterOneInvocation() {
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            return null;
        });
        respondProblem(503, problem(503, "SERVICE_UNAVAILABLE").replace("safe-detail", PRIVATE_DETAIL));

        assertMapperConfigurationFailure(mappedApi);

        assertEquals(1, invocationCount.get());
    }

    @Test
    void test_rawCauseDependencyMapperResultFailsClosedAfterOneInvocation() {
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            return (RuntimeException) cause;
        });
        respondProblem(503, problem(503, "SERVICE_UNAVAILABLE").replace("safe-detail", PRIVATE_DETAIL));

        assertMapperConfigurationFailure(mappedApi);

        assertEquals(1, invocationCount.get());
    }

    @Test
    void test_missingCauseDependencyMapperResultFailsClosedAfterOneInvocation() {
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            return new TargetDependencyFailure(kind, null);
        });
        respondProblem(503, problem(503, "SERVICE_UNAVAILABLE").replace("safe-detail", PRIVATE_DETAIL));

        assertMapperConfigurationFailure(mappedApi);

        assertEquals(1, invocationCount.get());
    }

    @Test
    void test_wrongCauseDependencyMapperResultFailsClosedAfterOneInvocation() {
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = mappedApi((kind, cause) -> {
            invocationCount.incrementAndGet();
            return new TargetDependencyFailure(kind, new IllegalStateException(PRIVATE_DETAIL));
        });
        respondProblem(503, problem(503, "SERVICE_UNAVAILABLE").replace("safe-detail", PRIVATE_DETAIL));

        assertMapperConfigurationFailure(mappedApi);

        assertEquals(1, invocationCount.get());
    }

    @Test
    void test_nestedHttpTimeoutCauseMapsToTimeoutWithoutExternalInfrastructure() {
        HttpTimeoutException timeout = new HttpTimeoutException("safe timeout");
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = transportFailureApi(new IOException(new IllegalStateException(timeout)), (kind, cause) -> {
            invocationCount.incrementAndGet();
            return new TargetDependencyFailure(kind, cause);
        });

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.TIMEOUT, failure.kind);
        ResourceAccessException cause = assertInstanceOf(ResourceAccessException.class, failure.getCause());
        assertSame(timeout, cause.getCause().getCause().getCause());
        assertEquals(0, simulator.requests().size());
    }

    @Test
    void test_nestedSocketTimeoutCauseMapsToTimeoutWithoutExternalInfrastructure() {
        SocketTimeoutException timeout = new SocketTimeoutException("safe socket timeout");
        AtomicInteger invocationCount = new AtomicInteger();
        ResourceApi mappedApi = transportFailureApi(new IOException(new IllegalArgumentException(timeout)), (kind, cause) -> {
            invocationCount.incrementAndGet();
            return new TargetDependencyFailure(kind, cause);
        });

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.TIMEOUT, failure.kind);
        ResourceAccessException cause = assertInstanceOf(ResourceAccessException.class, failure.getCause());
        assertSame(timeout, cause.getCause().getCause().getCause());
        assertEquals(0, simulator.requests().size());
    }

    @Test
    void test_nestedConnectTimeoutWinsOverSocketTimeoutAndMapsToUnavailableWithoutRetry() {
        HttpConnectTimeoutException connectTimeout = new HttpConnectTimeoutException("connect-timeout-diagnostic");
        SocketTimeoutException socketTimeout = new SocketTimeoutException("socket-timeout-diagnostic");
        connectTimeout.initCause(socketTimeout);
        AtomicInteger invocationCount = new AtomicInteger();
        MapperInvocation[] invocation = new MapperInvocation[1];
        ResourceApi mappedApi = transportFailureApi(new IOException(new IllegalStateException(connectTimeout)),
            (kind, cause) -> {
                invocationCount.incrementAndGet();
                invocation[0] = new MapperInvocation(kind, cause);
                return new TargetDependencyFailure(kind, cause);
            });

        TargetDependencyFailure failure = assertThrows(TargetDependencyFailure.class, mappedApi::read);

        assertEquals(1, invocationCount.get());
        assertEquals(HttpServiceClientOptions.DependencyFailureKind.UNAVAILABLE, failure.kind);
        assertSame(invocation[0].cause(), failure.getCause());
        ResourceAccessException cause = assertInstanceOf(ResourceAccessException.class, failure.getCause());
        assertSame(connectTimeout, cause.getCause().getCause().getCause());
        assertFalse(failure.getMessage().contains("connect-timeout-diagnostic"));
        assertFalse(failure.getMessage().contains("socket-timeout-diagnostic"));
        assertEquals(0, simulator.requests().size());
    }

    @Test
    void test_non2xxHeadRejectsMissingOrInvalidProtocolHeaders() {
        simulator.respond(404, "", "application/json", requestHeaders());
        assertInvalidResponse(api::head);
        simulator.respond(404, "", "application/problem+json", Map.of());
        assertInvalidResponse(api::head);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308})
    void test_redirectedVoidCommandIsRejectedWithoutFollowingOrRepeating(int status) {
        simulator.respond(status, problem(400, "BUSINESS_CHECK_FAILED"), "application/problem+json", requestHeaders());

        assertInvalidResponse(api::update);

        assertEquals(List.of("POST /resource"), simulator.requests());
        assertEquals(0, simulator.version());
    }

    @Test
    void test_redirectedTypedResponseIsRejectedBeforeBodyConversion() {
        simulator.respond(302, "{\"version\":999}", "application/json");

        assertInvalidResponse(api::read);

        assertEquals(1, simulator.requests().size());
    }

    @Test
    void test_successfulWrongContentTypeIsInvalidResponse() {
        simulator.respond(200, PRIVATE_DETAIL, "text/plain");

        assertInstanceOf(RestClientException.class, assertInvalidResponse(api::read).getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null"})
    void test_requiredTypedBodyCannotBeNull(String body) {
        simulator.respond(200, body, "application/json");

        assertInvalidResponse(api::read);
    }

    @Test
    void test_noContentIsValidForVoidAndBoxedButInvalidForRequiredDto() {
        simulator.respond(204, "", "application/json");

        api.update();
        assertNull(api.updateBoxed());
        assertEquals(HttpStatus.NO_CONTENT, api.updateEntity().getStatusCode());
        assertInvalidResponse(api::read);
    }

    @Test
    void test_unexpectedRuntimePropagatesUnchanged() {
        IllegalStateException original = new IllegalStateException(PRIVATE_DETAIL);
        RestClient client = client(Duration.ofSeconds(2)).mutate()
            .requestInterceptor((request, body, execution) -> { throw original; }).build();
        ResourceApi faultyApi = HttpServiceClientFactory.create(client).createClient(ResourceApi.class);

        assertSame(original, assertThrows(IllegalStateException.class, faultyApi::read));
        assertEquals(0, simulator.requests().size());
    }

    @Test
    void test_duplicateContractCodeIsRejectedAtConfigurationTime() {
        assertThrows(IllegalArgumentException.class, () -> HttpServiceClientFactory.create(
            client(Duration.ofSeconds(2)), ContractError.SESSION_EXPIRED, ContractError.SESSION_EXPIRED));
    }

    @ParameterizedTest
    @EnumSource(CommonErrorCode.class)
    void test_publicCodesCannotBeRegisteredAsSpecialFailures(CommonErrorCode reserved) {
        CodeMessageEnum conflicting = new CodeMessageEnum() {
            @Override public String getCode() { return reserved.getCode(); }
            @Override public String getMessage() { return "伪装的特殊错误"; }
        };

        assertThrows(IllegalArgumentException.class,
            () -> HttpServiceClientFactory.create(client(Duration.ofSeconds(2)), conflicting));
    }

    @Test
    void test_connectionAndTimeoutFailuresRemainUnavailableAndKeepTheirTransportCauseWithoutRetry() throws IOException {
        simulator.close();
        ServiceUnavailableException connection = assertThrows(ServiceUnavailableException.class, api::read);
        assertInstanceOf(ResourceAccessException.class, connection.getCause());

        simulator = new HttpServiceSimulator();
        simulator.delayResponse();
        ResourceApi timeoutApi = HttpServiceClientFactory.create(client(Duration.ofMillis(100)))
            .createClient(ResourceApi.class);
        ServiceUnavailableException timeout = assertThrows(ServiceUnavailableException.class, timeoutApi::read);
        assertInstanceOf(ResourceAccessException.class, timeout.getCause());
        assertEquals(1, simulator.requests().size());
    }

    @Test
    void test_successfulMalformedJsonIsInvalidResponseAndPreservesCause() {
        simulator.respond(200, PRIVATE_DETAIL + "{", "application/json");

        ErrorException failure = assertInvalidResponse(api::read);

        assertInstanceOf(RestClientException.class, failure.getCause());
        assertNotNull(failure.getCause().getCause());
    }

    @Test
    void test_defaultMethodIsNotTreatedAsRemoteEmptyBody() {
        assertNull(api.localDefault());
        assertEquals(0, simulator.requests().size());
    }

    private void respondProblem(int status, String body) {
        simulator.respond(status, body, "application/problem+json", requestHeaders());
    }

    private Map<String, String> requestHeaders() {
        return Map.of("X-Request-Id", REQUEST_ID);
    }

    private ResourceApi migrationApi() {
        return HttpServiceClientFactory.create(
            client(Duration.ofSeconds(2)), HttpServiceClientOptions.migrationWithLegacyErrorResponse(), ContractError.values()
        ).createClient(ResourceApi.class);
    }

    private ResourceApi mappedApi(HttpServiceClientOptions.DependencyFailureMapper mapper) {
        return mappedApi(Duration.ofSeconds(2), mapper);
    }

    private ResourceApi mappedApi(Duration readTimeout, HttpServiceClientOptions.DependencyFailureMapper mapper) {
        return HttpServiceClientFactory.create(client(readTimeout),
            HttpServiceClientOptions.strict().withDependencyFailureMapper(mapper)).createClient(ResourceApi.class);
    }

    private ResourceApi transportFailureApi(IOException transportCause,
        HttpServiceClientOptions.DependencyFailureMapper mapper) {
        ClientHttpRequestFactory failingRequestFactory = (uri, method) -> { throw transportCause; };
        RestClient failingClient = RestClient.builder().baseUrl(simulator.baseUrl()).requestFactory(failingRequestFactory).build();
        return HttpServiceClientFactory.create(failingClient,
            HttpServiceClientOptions.strict().withDependencyFailureMapper(mapper)).createClient(ResourceApi.class);
    }

    private RestClient client(Duration readTimeout) {
        HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(transport);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(simulator.baseUrl()).requestFactory(requestFactory).build();
    }

    private ErrorException assertInvalidResponse(org.junit.jupiter.api.function.Executable operation) {
        ErrorException failure = assertThrows(ErrorException.class, operation);
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), failure.getCode());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getMessage(), failure.getMessage());
        return failure;
    }

    private void assertMapperConfigurationFailure(ResourceApi mappedApi) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, mappedApi::read);
        assertEquals("dependencyFailureMapper returned an invalid exception", failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(ErrorException.class.isInstance(failure));
        assertNoRemoteBodyOrParserDiagnostic(failure);
    }

    private String problem(int status, String code) {
        return "{\"type\":\"about:blank\",\"title\":\"" + HttpStatus.valueOf(status).getReasonPhrase()
            + "\",\"detail\":\"safe-detail\",\"code\":\"" + code + "\",\"requestId\":\"" + REQUEST_ID
            + "\",\"timestamp\":\"2026-09-06T10:20:30.123456Z\",\"errors\":[{\"field\":\"name\",\"message\":\"bad\"}]}";
    }

    private String legacy(String code) {
        return legacy(code, "legacy-request");
    }

    private String legacy(String code, String requestId) {
        return legacy(code, requestId, "2026-09-04T10:00:00Z");
    }

    private String legacy(String code, String requestId, String timestamp) {
        return "{\"code\":\"" + code + "\",\"message\":\"legacy-safe-detail\",\"requestId\":\""
            + requestId + "\",\"timestamp\":\"" + timestamp + "\"}";
    }

    private void assertNoRemoteBodyOrParserDiagnostic(Throwable failure) {
        assertNoRemoteBodyOrParserDiagnostic(failure, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void assertNoRemoteBodyOrParserDiagnostic(Throwable failure, Set<Throwable> seen) {
        if (failure == null || !seen.add(failure)) {
            return;
        }
        assertFalse(String.valueOf(failure.getMessage()).contains(PRIVATE_DETAIL));
        assertFalse(String.valueOf(failure.getMessage()).contains("Unexpected character"));
        assertNoRemoteBodyOrParserDiagnostic(failure.getCause(), seen);
        for (Throwable suppressed : failure.getSuppressed()) {
            assertNoRemoteBodyOrParserDiagnostic(suppressed, seen);
        }
    }

    public interface ResourceApi {
        @GetExchange("/resource") ResourceVersion read();
        @GetExchange("/resource") HttpHeaders headers();
        @GetExchange("/resource") ResponseEntity<ResourceVersion> readEntity();
        @PostExchange("/resource") void update();
        @PostExchange("/resource") Void updateBoxed();
        @PostExchange("/resource") ResponseEntity<Void> updateEntity();
        @HttpExchange(url = "/resource", method = "HEAD") void head();
        default String localDefault() { return null; }
    }

    public record ResourceVersion(int version) {}

    private record MapperInvocation(HttpServiceClientOptions.DependencyFailureKind kind, Throwable cause) {}

    private static final class TargetDependencyFailure extends RuntimeException {

        private final HttpServiceClientOptions.DependencyFailureKind kind;

        private TargetDependencyFailure(HttpServiceClientOptions.DependencyFailureKind kind, Throwable cause) {
            super("safe mapped dependency failure", cause);
            this.kind = kind;
        }
    }

    enum ContractError implements CodeMessageEnum {
        SESSION_EXPIRED;

        @Override public String getCode() { return "SESSION_EXPIRED"; }

        @Override public String getMessage() { return "登录状态已过期"; }
    }
}
