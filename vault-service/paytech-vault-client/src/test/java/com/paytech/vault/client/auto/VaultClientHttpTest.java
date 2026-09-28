package com.paytech.vault.client.auto;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.vault.client.*;
import com.paytech.vault.client.test.ClientContext;
import com.paytech.vault.client.test.VaultSimulator;

import tools.jackson.databind.json.JsonMapper;

class VaultClientHttpTest {
    private static final String TOKEN = VaultClientSafetyTest.TOKEN;
    private static final String PROBLEM_CONTENT_TYPE = "application/problem+json";
    private static final String OUTGOING_ID = "outgoing-id";
    private static final String CANONICAL_TIMESTAMP = "2026-09-06T10:20:30.123456Z";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ProtectCommand command() { return new ProtectCommand("EMAIL_ADDRESS_V1", TokenMode.STABLE,
            "TEST-ONLY-NOT-A-SECRET@example.test", "CHECKOUT_CONTACT", "logical-command-1"); }
    private String fixture(String name) throws Exception { return Files.readString(Path.of("../testdata/wire/" + name + ".json")); }

    private static String problem(int status, String code, String requestId, String timestamp,
            String detail, String additionalMembers) {
        return "{\"type\":\"about:blank\",\"title\":\"" + statusTitle(status)
                + "\",\"detail\":\"" + detail + "\",\"code\":\"" + code
                + "\",\"requestId\":\"" + requestId + "\",\"timestamp\":\"" + timestamp
                + "\"" + additionalMembers + "}";
    }

    private static String legacy(String code, String message, String requestId, String timestamp) {
        return "{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"requestId\":\"" + requestId + "\",\"timestamp\":\"" + timestamp + "\"}";
    }

    private static String statusTitle(int status) {
        org.springframework.http.HttpStatus resolved = org.springframework.http.HttpStatus.resolve(status);
        return resolved == null ? "Unknown" : resolved.getReasonPhrase();
    }

    private static String removeProperty(String json, String property) {
        if (json.contains(property + ",")) return json.replace(property + ",", "");
        if (json.contains("," + property)) return json.replace("," + property, "");
        throw new IllegalArgumentException("fixture property not present");
    }

    private static void assertVaultError(VaultClient client, Class<?> expectedClass, String expectedCode,
            String... remoteCanaries) {
        MDC.put("requestId", OUTGOING_ID);
        Throwable failure;
        try {
            failure = catchThrowable(() -> client.protect(command()));
        } finally {
            MDC.clear();
        }
        assertThat(failure).isExactlyInstanceOf(expectedClass).hasNoCause();
        assertThat(failure.getSuppressed()).isEmpty();
        String actualCode = failure instanceof FailException fail ? fail.getCode()
                : ((ErrorException) failure).getCode();
        assertThat(actualCode).isEqualTo(expectedCode);
        assertThat(failure.getMessage()).contains(OUTGOING_ID);
        if (remoteCanaries.length > 0) assertThat(failure.getMessage()).doesNotContain(remoteCanaries);
    }

    private static void assertInvalidProtocol(VaultSimulator simulator, VaultClient client, int status,
            String body, List<String> contentTypes, List<String> responseIds, String... remoteCanaries) {
        assertInvalidProtocol(simulator, client, status, body.getBytes(StandardCharsets.UTF_8), contentTypes,
                responseIds, remoteCanaries);
    }

    private static void assertInvalidProtocol(VaultSimulator simulator, VaultClient client, int status,
            byte[] body, List<String> contentTypes, List<String> responseIds, String... remoteCanaries) {
        int requestsBefore = simulator.requests.size();
        simulator.respond(status, body, contentTypes, responseIds);
        MDC.put("requestId", OUTGOING_ID);
        Throwable failure;
        try {
            failure = catchThrowable(() -> client.protect(command()));
        } finally {
            MDC.clear();
        }
        assertThat(failure).isExactlyInstanceOf(ErrorException.class).hasNoCause();
        assertThat(failure.getSuppressed()).isEmpty();
        ErrorException error = (ErrorException) failure;
        assertThat(error.getErrorCode()).isSameAs(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        assertThat(error.getCode()).isEqualTo("UPSTREAM_INVALID_RESPONSE");
        assertThat(error.getMessage()).contains(OUTGOING_ID);
        if (remoteCanaries.length > 0) assertThat(error.getMessage()).doesNotContain(remoteCanaries);
        assertThat(simulator.requests).hasSize(requestsBefore + 1);
    }

    private static boolean awaitBoundedRead(Thread requestThread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            for (StackTraceElement frame : requestThread.getStackTrace()) {
                if (frame.getClassName().equals("com.paytech.vault.client.internal.BoundedResponseBody")
                        && frame.getMethodName().equals("readBounded")) return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    @Test
    void test_defaultRequestCallbackCannotChangeProtocolHeaders() throws Exception {
        assertProtocolHeadersSurvive(builder -> builder.defaultRequest(spec -> spec.httpRequest(request -> {
            request.getHeaders().set("X-Caller-Service", "callback-caller");
            request.getHeaders().set("X-Request-Id", "callback-request");
            request.getHeaders().set("Idempotency-Key", "callback-key");
        })));
    }

    @Test
    void test_requestInitializerCannotDuplicateOrInjectProtocolHeaders() throws Exception {
        assertProtocolHeadersSurvive(builder -> builder.requestInitializer(request -> {
            request.getHeaders().add("X-Caller-Service", "initializer-caller");
            request.getHeaders().add("X-Request-Id", "initializer-request");
            request.getHeaders().add("Idempotency-Key", "initializer-key");
        }));
    }

    private void assertProtocolHeadersSurvive(org.springframework.boot.restclient.RestClientCustomizer customizer) throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"token\":\"" + TOKEN + "\"}");
            simulator.respond(200, "{\"value\":\"\"}");
            ClientContext.direct(simulator).withBean(org.springframework.boot.restclient.RestClientCustomizer.class, () -> customizer)
                    .run(context -> {
                        MDC.put("requestId", "original-request");
                        try { context.getBean(VaultClient.class).protect(command()); }
                        finally { MDC.clear(); }
                        context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P"));
                    });
            assertThat(simulator.requests).hasSize(2);
            var protect = simulator.requests.get(0).headers();
            var reveal = simulator.requests.get(1).headers();
            assertThat(protect.get("X-Caller-Service")).containsExactly("commerce-service");
            assertThat(reveal.get("X-Caller-Service")).containsExactly("commerce-service");
            assertThat(protect.get("X-Request-Id")).containsExactly("original-request");
            assertThat(reveal.get("X-Request-Id")).singleElement().asString()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(protect.get("Idempotency-Key")).containsExactly("logical-command-1");
            assertThat(reveal.get("Idempotency-Key")).isNull();
        }
    }

    @Test
    void test_customUriBuilderFactoryCannotReplaceConfiguredTarget() throws Exception {
        try (var target = new VaultSimulator(); var other = new VaultSimulator()) {
            target.respond(200, "{\"value\":\"configured-target\"}");
            other.respond(200, "{\"value\":\"wrong-target\"}");
            ClientContext.direct(target).withBean(org.springframework.boot.restclient.RestClientCustomizer.class,
                    () -> builder -> builder.uriBuilderFactory(new org.springframework.web.util.DefaultUriBuilderFactory(other.url())))
                    .run(context -> assertThat(context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P")).value())
                            .isEqualTo("configured-target"));
            assertThat(target.requests).hasSize(1);
            assertThat(other.requests).isEmpty();
        }
    }

    @Test
    void test_nonUtf8ContentTypeFailsClosed() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"value\":\"é\"}".getBytes(StandardCharsets.UTF_8),
                    List.of("application/json;charset=ISO-8859-1"), List.of());
            ClientContext.direct(simulator).run(context -> assertThatThrownBy(() -> context.getBean(VaultClient.class)
                    .reveal(new RevealQuery(TOKEN, "P"))).isExactlyInstanceOf(ErrorException.class).hasNoCause());
        }
    }

    @Test
    void test_connectionRefusalIsUnavailable() throws Exception {
        var simulator = new VaultSimulator();
        var runner = ClientContext.direct(simulator);
        simulator.close();
        runner.run(context -> assertThatThrownBy(() -> context.getBean(VaultClient.class).protect(command()))
                .isExactlyInstanceOf(ServiceUnavailableException.class).hasNoCause());
    }

    @Test
    void test_readTimeoutAlsoBoundsStalledBody() throws Exception {
        try (var simulator = new VaultSimulator()) {
            var gate = new CountDownLatch(1);
            simulator.respondWithBodyGate(200, "{\"value\":\"never-returned\"}", gate);
            ClientContext.direct(simulator).withPropertyValues("vault.client.read-timeout=100ms").run(context -> {
                try {
                    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () ->
                            assertThatThrownBy(() -> context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P")))
                                    .isExactlyInstanceOf(ServiceUnavailableException.class).hasNoCause());
                } finally { gate.countDown(); }
            });
        }
    }

    @Test
    void test_configuredCallerAndDuplicateDefaultHeadersCannotOverrideContract() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"value\":\"\"}");
            ClientContext.direct(simulator).withPropertyValues("vault.client.caller-service=payment-service")
                    .withBean(org.springframework.boot.restclient.RestClientCustomizer.class, () -> builder -> builder
                            .defaultHeader("X-Caller-Service", "spoof-one", "spoof-two")
                            .defaultHeader("Idempotency-Key", "unexpected-default"))
                    .run(context -> {
                        context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P"));
                        assertThat(simulator.requests.getFirst().headers().get("X-Caller-Service")).containsExactly("payment-service");
                        assertThat(simulator.requests.getFirst().headers().get("Idempotency-Key")).isNull();
                    });
        }
    }

    @Test
    void test_wireFixturesAndFixedHeaders() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, fixture("protect-response")); simulator.respond(200, fixture("reveal-response"));
            String protectRequest = fixture("protect-request"), revealRequest = fixture("reveal-request");
            ClientContext.direct(simulator).run(context -> {
                assertThat(context).hasNotFailed();
                VaultClient client = context.getBean(VaultClient.class);
                MDC.put("requestId", "r".repeat(64));
                try {
                    ProtectResult protectedValue = client.protect(command());
                    assertThat(protectedValue.token()).isEqualTo("A".repeat(43));
                    assertThat(client.reveal(new RevealQuery(protectedValue.token(), "CHECKOUT_CONTACT")).value())
                            .isEqualTo("TEST-ONLY-NOT-A-SECRET@example.test");
                } finally { MDC.clear(); }
                assertThat(simulator.requests).hasSize(2);
                var protect = simulator.requests.get(0); var reveal = simulator.requests.get(1);
                assertThat(protect.method()).isEqualTo("POST");
                assertThat(reveal.method()).isEqualTo("POST");
                assertThat(protect.path()).isEqualTo("/vault/v1/records/protect");
                assertThat(reveal.path()).isEqualTo("/vault/v1/records/reveal");
                assertThat(protect.headers().get("X-Caller-Service")).containsExactly("commerce-service");
                assertThat(protect.headers().get("Idempotency-Key")).containsExactly("logical-command-1");
                assertThat(reveal.headers().get("Idempotency-Key")).isNull();
                assertThat(protect.headers().get("X-Request-Id")).containsExactly("r".repeat(64));
                assertThat(JSON.readTree(protect.body())).isEqualTo(JSON.readTree(protectRequest));
                assertThat(JSON.readTree(reveal.body())).isEqualTo(JSON.readTree(revealRequest));
            });
        }
    }

    @Test
    void test_invalidRequestIdsBecomeUuidAndNullInputNeverCalls() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                var client = context.getBean(VaultClient.class);
                assertThatThrownBy(() -> client.protect(null)).isInstanceOf(FailException.class);
                assertThatThrownBy(() -> client.reveal(null)).isInstanceOf(FailException.class);
                for (String id : new String[]{"a", "r".repeat(64)}) {
                    simulator.respond(200, "{\"value\":\"\"}"); MDC.put("requestId", id);
                    try { assertThat(client.reveal(new RevealQuery(TOKEN, "P")).value()).isEmpty(); }
                    finally { MDC.clear(); }
                }
                for (String id : new String[]{"", "~legacy-only", "_leading", "-leading", "unsafe:id",
                        "a".repeat(65), "unsafe,id", "\n"}) {
                    simulator.respond(200, "{\"value\":\"\"}"); MDC.put("requestId", id);
                    try { assertThat(client.reveal(new RevealQuery(TOKEN, "P")).value()).isEmpty(); }
                    finally { MDC.clear(); }
                }
                assertThat(simulator.requests).hasSize(10);
                assertThat(simulator.requests.get(0).headers().getFirst("X-Request-Id")).isEqualTo("a");
                assertThat(simulator.requests.get(1).headers().getFirst("X-Request-Id")).isEqualTo("r".repeat(64));
                simulator.requests.subList(2, 10).forEach(request -> assertThat(request.headers().getFirst("X-Request-Id"))
                        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
            });
        }
    }

    @Test
    void test_validProblemCodesUseExactStatusAndSafeLocalExceptionsWithoutRetry() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                Object[][] cases = {
                    {400, "INVALID_REQUEST", FailException.class},
                    {400, "VALUE_TOO_LARGE", FailException.class},
                    {400, "UNSUPPORTED_DATA_TYPE", FailException.class},
                    {400, "TOKEN_MODE_NOT_ALLOWED", FailException.class},
                    {403, "ACCESS_DENIED", FailException.class},
                    {404, "TOKEN_NOT_FOUND", FailException.class},
                    {409, "IDEMPOTENCY_KEY_CONFLICT", FailException.class},
                    {500, "INTEGRITY_CHECK_FAILED", ErrorException.class},
                    {500, "INTERNAL_ERROR", ErrorException.class},
                    {503, "DEPENDENCY_UNAVAILABLE", ServiceUnavailableException.class},
                    {503, "AUDIT_UNAVAILABLE", ServiceUnavailableException.class}
                };
                for (int index = 0; index < cases.length; index++) {
                    int status = (int) cases[index][0];
                    String code = (String) cases[index][1];
                    String responseId = "response-" + index;
                    String detail = "REMOTE_PROBLEM_DETAIL_" + code;
                    String errors = index == 0
                            ? ",\"errors\":[{\"field\":\"value\",\"message\":\"safe validation message\"}]"
                            : "";
                    String body = problem(status, code, responseId, CANONICAL_TIMESTAMP, detail,
                            errors + ",\"future\":{\"unicode\":\"扩展😀\",\"accepted\":true}");
                    simulator.respond(status, body, PROBLEM_CONTENT_TYPE, List.of(responseId));
                    assertVaultError(client, (Class<?>) cases[index][2], code, detail, responseId);
                }
                assertThat(simulator.requests).hasSize(cases.length);
            });
        }
    }

    @Test
    void test_validLegacyTimestampFractionsAndLegacyOnlyRequestIdRemainMigrationCompatible() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String responseId = "~legacy-response-id";
                for (String timestamp : List.of("2026-09-06T10:20:30Z", "2026-09-06T10:20:30.1Z",
                        "2026-09-06T10:20:30.123Z", "2026-09-06T10:20:30.123456Z",
                        "2026-09-06T10:20:30.123456789Z")) {
                    String message = "REMOTE_LEGACY_MESSAGE_" + timestamp;
                    simulator.respond(409, legacy("IDEMPOTENCY_KEY_CONFLICT", message, responseId, timestamp),
                            "Application/Json; charset=\"UTF-8\"; profile=legacy", List.of(responseId));
                    assertVaultError(client, FailException.class, "IDEMPOTENCY_KEY_CONFLICT", message, responseId);
                }
                assertThat(simulator.requests).hasSize(5);
            });
        }
    }

    @Test
    void test_problemRequiredMembersRejectMissingNullWrongTypeAndBlankValues() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "response-required";
                String valid = problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", "");
                String[][] members = {
                    {"type", "\"about:blank\""},
                    {"title", "\"Bad Request\""},
                    {"detail", "\"safe detail\""},
                    {"code", "\"INVALID_REQUEST\""},
                    {"requestId", "\"response-required\""},
                    {"timestamp", "\"" + CANONICAL_TIMESTAMP + "\""}
                };
                for (String[] member : members) {
                    String field = member[0];
                    String literal = member[1];
                    String property = "\"" + field + "\":" + literal;
                    assertInvalidProtocol(simulator, client, 400, removeProperty(valid, property),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                    for (String invalid : List.of("null", "42", "\" \t\"")) {
                        assertInvalidProtocol(simulator, client, 400, valid.replace(property, "\"" + field + "\":" + invalid),
                                List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                    }
                }
                assertThat(simulator.requests).hasSize(24);
            });
        }
    }

    @Test
    void test_problemForbiddenMembersAndInvalidFieldErrorsFailClosed() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "response-errors";
                for (String forbidden : List.of(",\"status\":400", ",\"instance\":\"/vault\"",
                        ",\"message\":\"REMOTE_TOP_LEVEL_MESSAGE\"")) {
                    assertInvalidProtocol(simulator, client, 400,
                            problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", forbidden),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id), "REMOTE_TOP_LEVEL_MESSAGE");
                }
                for (String errors : List.of("null", "[]", "{}", "[null]", "[\"bad\"]", "[{}]",
                        "[{\"field\":\"value\"}]", "[{\"message\":\"bad\"}]",
                        "[{\"field\":null,\"message\":\"bad\"}]",
                        "[{\"field\":42,\"message\":\"bad\"}]",
                        "[{\"field\":\" \",\"message\":\"bad\"}]",
                        "[{\"field\":\"value\",\"message\":null}]",
                        "[{\"field\":\"value\",\"message\":42}]",
                        "[{\"field\":\"value\",\"message\":\" \"}]",
                        "[{\"field\":\"value\",\"message\":\"bad\",\"extra\":true}]",
                        "[{\"field\":\"value\",\"field\":\"other\",\"message\":\"bad\"}]",
                        "[{\"field\":\"value\",\"f\\u0069eld\":\"other\",\"message\":\"bad\"}]")) {
                    assertInvalidProtocol(simulator, client, 400,
                            problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail",
                                    ",\"errors\":" + errors),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                }
                assertThat(simulator.requests).hasSize(20);
            });
        }
    }

    @Test
    void test_problemTypeTitleTimestampAndRequestIdMustMatchTheProfile() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "response-profile";
                for (String type : List.of("https://example.test/problem", "ABOUT:BLANK")) {
                    assertInvalidProtocol(simulator, client, 400,
                            problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", "")
                                    .replace("\"type\":\"about:blank\"", "\"type\":\"" + type + "\""),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                }
                assertInvalidProtocol(simulator, client, 400,
                        problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", "")
                                .replace("\"title\":\"Bad Request\"", "\"title\":\"Conflict\""),
                        List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                for (String timestamp : List.of("2026-09-06T10:20:30Z", "2026-09-06T10:20:30.1Z",
                        "2026-09-06T10:20:30.123Z", "2026-09-06T10:20:30.12345Z",
                        "2026-09-06T10:20:30.1234567Z", "2026-09-06T10:20:30.123456+00:00",
                        "2026-09-06T10:20:30.123456z", "2026-02-30T10:20:30.123456Z",
                        "026-09-06T10:20:30.123456Z", "12026-09-06T10:20:30.123456Z")) {
                    assertInvalidProtocol(simulator, client, 400,
                            problem(400, "INVALID_REQUEST", id, timestamp, "safe detail", ""),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                }
                for (String invalidId : List.of("_leading", "-leading", "~legacy", "bad:id", "a".repeat(65))) {
                    assertInvalidProtocol(simulator, client, 400,
                            problem(400, "INVALID_REQUEST", invalidId, CANONICAL_TIMESTAMP, "safe detail", ""),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(invalidId));
                }
                assertInvalidProtocol(simulator, client, 400,
                        problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", ""),
                        List.of(PROBLEM_CONTENT_TYPE), List.of());
                assertInvalidProtocol(simulator, client, 400,
                        problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", ""),
                        List.of(PROBLEM_CONTENT_TYPE), List.of(id, "second-response"));
                assertInvalidProtocol(simulator, client, 400,
                        problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", ""),
                        List.of(PROBLEM_CONTENT_TYPE), List.of("different-response"));
                assertThat(simulator.requests).hasSize(21);
            });
        }
    }

    @Test
    void test_legacyObjectAndResponseIdRemainStrict() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "legacy-id";
                String valid = legacy("INVALID_REQUEST", "允许但绝不外显😀", id, "2026-09-06T10:20:30Z");
                for (String body : List.of("", "null", "[]", "{}",
                        valid.replace("\"message\":\"允许但绝不外显😀\"", "\"message\":null"),
                        valid.replace("\"message\":\"允许但绝不外显😀\"", "\"message\":\" \""),
                        valid.replace("\"requestId\":\"legacy-id\"", "\"requestId\":\"bad:id\""),
                        valid.replace("2026-09-06T10:20:30Z", "2026-09-06T10:20:30z"),
                        valid.replace("2026-09-06T10:20:30Z", "2026-02-30T10:20:30Z"),
                        valid.substring(0, valid.length() - 1) + ",\"extra\":true}",
                        valid.replace("允许但绝不外显😀", "\\ud800"))) {
                    assertInvalidProtocol(simulator, client, 400, body, List.of("application/json"), List.of(id),
                            "允许但绝不外显😀");
                }
                assertInvalidProtocol(simulator, client, 400, valid, List.of("application/json"), List.of());
                assertInvalidProtocol(simulator, client, 400, valid, List.of("application/json"), List.of(id, "other"));
                assertInvalidProtocol(simulator, client, 400, valid, List.of("application/json"), List.of("mismatch"));
                assertThat(simulator.requests).hasSize(14);
            });
        }
    }

    @Test
    void test_strictJsonUtf8AndRawBodyLimitApplyBeforeErrorClassification() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "response-json";
                for (String body : List.of("", "{broken", "null", "[]", "true", "42", "\"literal\"",
                        "{}{}", "{\"code\":\"INVALID_REQUEST\",\"code\":\"ACCESS_DENIED\"}",
                        "{\"code\":\"INVALID_REQUEST\",\"c\\u006fde\":\"ACCESS_DENIED\"}",
                        problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail",
                                ",\"errors\":[{\"field\":\"value\",\"f\\u0069eld\":\"other\",\"message\":\"bad\"}]")
                                + "null")) {
                    assertInvalidProtocol(simulator, client, 400, body, List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                }
                byte[] malformedUtf8 = {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, 0x28, '"', '}'};
                assertInvalidProtocol(simulator, client, 400, malformedUtf8,
                        List.of(PROBLEM_CONTENT_TYPE), List.of(id));

                String valid = problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", "");
                byte[] exact = (valid + " ".repeat(262144 - valid.getBytes(StandardCharsets.UTF_8).length))
                        .getBytes(StandardCharsets.UTF_8);
                assertThat(exact).hasSize(262144);
                simulator.respond(400, exact, List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                assertVaultError(client, FailException.class, "INVALID_REQUEST", "safe detail", id);

                byte[] over = (valid + " ".repeat(262145 - valid.getBytes(StandardCharsets.UTF_8).length))
                        .getBytes(StandardCharsets.UTF_8);
                assertThat(over).hasSize(262145);
                assertInvalidProtocol(simulator, client, 400, over, List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                assertThat(simulator.requests).hasSize(14);
            });
        }
    }

    @Test
    void test_errorMediaTypeDispatchIsExactAndCrossMediaBodiesFailClosed() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "response-media";
                String newBody = problem(400, "INVALID_REQUEST", id, CANONICAL_TIMESTAMP, "safe detail", "");
                String oldBody = legacy("INVALID_REQUEST", "REMOTE_LEGACY_MEDIA", id, "2026-09-06T10:20:30Z");
                simulator.respond(400, newBody, "Application/Problem+Json; charset=UTF-8; profile=\"vault\"", List.of(id));
                assertVaultError(client, FailException.class, "INVALID_REQUEST", "safe detail", id);

                assertInvalidProtocol(simulator, client, 400, newBody, List.of("application/json"), List.of(id));
                assertInvalidProtocol(simulator, client, 400, oldBody, List.of(PROBLEM_CONTENT_TYPE), List.of(id),
                        "REMOTE_LEGACY_MEDIA");
                for (List<String> contentTypes : List.of(List.<String>of(), List.of("text/plain"),
                        List.of(PROBLEM_CONTENT_TYPE, PROBLEM_CONTENT_TYPE),
                        List.of("application/problem+json, application/json"),
                        List.of("application/problem+json; charset=ISO-8859-1"),
                        List.of("application/problem+json; charset=UTF-8; charset=UTF-8"),
                        List.of("application/problem+json; broken"))) {
                    assertInvalidProtocol(simulator, client, 400, newBody, contentTypes, List.of(id));
                }
                assertThat(simulator.requests).hasSize(10);
            });
        }
    }

    @Test
    void test_unknownMismatchedUnsupportedAndRedirectResponsesAreProtocolErrorsWithoutRetry() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                String id = "response-status";
                Object[][] invalidPairs = {
                    {400, "ACCESS_DENIED"}, {403, "INVALID_REQUEST"}, {404, "IDEMPOTENCY_KEY_CONFLICT"},
                    {409, "TOKEN_NOT_FOUND"}, {500, "DEPENDENCY_UNAVAILABLE"}, {503, "INTERNAL_ERROR"},
                    {400, "UNKNOWN_CODE"}
                };
                for (Object[] invalid : invalidPairs) {
                    int status = (int) invalid[0];
                    assertInvalidProtocol(simulator, client, status,
                            problem(status, (String) invalid[1], id, CANONICAL_TIMESTAMP, "safe detail", ""),
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                }
                for (int status : new int[]{201, 202, 301, 302, 307, 401, 405, 408, 410, 413, 415, 422, 429,
                        431, 502, 504, 599}) {
                    String title = statusTitle(status);
                    String body = "{\"type\":\"about:blank\",\"title\":\"" + title
                            + "\",\"detail\":\"safe detail\",\"code\":\"INVALID_REQUEST\",\"requestId\":\""
                            + id + "\",\"timestamp\":\"" + CANONICAL_TIMESTAMP + "\"}";
                    assertInvalidProtocol(simulator, client, status, body,
                            List.of(PROBLEM_CONTENT_TYPE), List.of(id));
                }
                assertThat(simulator.requests).hasSize(24);
            });
        }
    }

    @Test
    void test_statusWinsAndRemoteMessageNeverLeaks() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                for (int status : new int[]{201, 202, 400, 401, 403, 404, 409, 429, 500, 502, 503, 504, 302}) {
                    String responseId = "safe-request";
                    simulator.respond(status, legacy("INTEGRITY_CHECK_FAILED", TOKEN, responseId,
                            "2026-09-04T00:00:00Z"), "application/json", List.of(responseId));
                    MDC.put("requestId", OUTGOING_ID);
                    Throwable failure;
                    try {
                        failure = catchThrowable(() -> context.getBean(VaultClient.class)
                                .reveal(new RevealQuery(TOKEN, "P")));
                    } finally {
                        MDC.clear();
                    }
                    assertThat(failure).isExactlyInstanceOf(ErrorException.class).hasNoCause()
                            .hasMessageNotContaining(TOKEN).hasMessageContaining(OUTGOING_ID);
                    assertThat(failure.getSuppressed()).isEmpty();
                    String expectedCode = status == 500 ? "INTEGRITY_CHECK_FAILED" : "UPSTREAM_INVALID_RESPONSE";
                    assertThat(((ErrorException) failure).getCode()).isEqualTo(expectedCode);
                }
                assertThat(simulator.requests).hasSize(13);
            });
        }
    }

    @Test
    void test_errorCodeAllowlistAndMalformedErrorStatusFamilies() throws Exception {
        try (var simulator = new VaultSimulator()) {
            String conflict = fixture("error-response");
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                simulator.respond(409, conflict, "application/json", List.of("test-request-id-0001"));
                assertVaultError(client, FailException.class, "IDEMPOTENCY_KEY_CONFLICT",
                        "test-request-id-0001");
                for (int status : new int[]{400, 503, 500}) {
                    for (String body : new String[]{"", "{broken", "null", " ".repeat(262145),
                            "[]", "{\"code\":\"ACCESS_DENIED\"}",
                            "{\"code\":\"ACCESS_DENIED\",\"message\":42,\"requestId\":\"ok\",\"timestamp\":\"2026-09-04T00:00:00Z\"}",
                            "{\"code\":\"ACCESS_DENIED\",\"message\":\"ignored\",\"requestId\":\"bad,id\",\"timestamp\":\"2026-09-04T00:00:00Z\"}",
                            "{\"code\":\"ACCESS_DENIED\",\"message\":\"ignored\",\"requestId\":\"ok\",\"timestamp\":\"invalid\"}",
                            "{\"code\":\"" + TOKEN + "\",\"message\":\"" + TOKEN + "\",\"requestId\":\"ok\",\"timestamp\":\"2026-09-04T00:00:00Z\"}"}) {
                        assertInvalidProtocol(simulator, client, status, body, List.of("application/json"),
                                List.of("ok"), TOKEN);
                    }
                }
                assertThat(simulator.requests).hasSize(31);
            });
        }
    }

    @Test
    void test_boundaryValuesAndNoNormalization() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                for (String value : new String[]{"", " \r\nÉ 😀 � ", "é".repeat(16384)}) {
                    simulator.respond(200, JSON.writeValueAsString(java.util.Map.of("value", value)));
                    assertThat(context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P")).value()).isEqualTo(value);
                }
                String valid = "{\"value\":\"x\"}";
                simulator.respond(200, valid + " ".repeat(262144 - valid.length()));
                assertThat(context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P")).value()).isEqualTo("x");
                for (String body : new String[]{"{\"token\":null}", "{\"token\":\"short\"}", "{\"Token\":\"" + TOKEN + "\"}"}) {
                    simulator.respond(200, body);
                    assertThatThrownBy(() -> context.getBean(VaultClient.class).protect(command())).isExactlyInstanceOf(ErrorException.class);
                }
            });
        }
    }

    @Test
    void test_closedSuccessShapeAndRawBodyLimit() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                for (String body : new String[]{"", "{}", "null", "[]", "{\"value\":null}", "{\"value\":1}",
                        "{\"Value\":\"x\"}", "{\"value\":\"x\",\"extra\":1}", "{\"value\":\"x\",\"value\":\"y\"}",
                        "{\"value\":\"x\",\"v\\u0061lue\":\"y\"}", "{\"value\":\"x\"}{}", "{\"value\":\"\\ud800\"}",
                        "{\"value\":\"" + "é".repeat(16385) + "\"}", " ".repeat(262145)}) {
                    simulator.respond(200, body);
                    assertThatThrownBy(() -> context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P")))
                            .isExactlyInstanceOf(ErrorException.class).hasNoCause();
                }
                simulator.respond(200, new byte[]{'{','"','v','a','l','u','e','"',':','"',(byte)0xc3,0x28,'"','}'}, false, null);
                assertThatThrownBy(() -> context.getBean(VaultClient.class).reveal(new RevealQuery(TOKEN, "P")))
                        .isExactlyInstanceOf(ErrorException.class).hasNoCause();
            });
        }
    }

    @Test
    void test_oversizeStreamingResponseAbortsWithoutDrainingTheTail() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).withPropertyValues("vault.client.read-timeout=10s").run(context -> {
                var probe = simulator.respondWithStreamingTail(400);
                var completed = new CountDownLatch(1);
                var observed = new AtomicReference<Throwable>();
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    executor.submit(() -> {
                        MDC.put("requestId", OUTGOING_ID);
                        try {
                            observed.set(catchThrowable(() -> context.getBean(VaultClient.class).protect(command())));
                        } finally {
                            MDC.clear();
                            completed.countDown();
                        }
                    });
                    assertThat(probe.awaitPrefix()).isTrue();
                    try {
                        assertThat(completed.await(2, TimeUnit.SECONDS))
                                .as("the client must abort after the bounded prefix without waiting for the tail")
                                .isTrue();
                    } finally {
                        probe.releaseTail();
                        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
                    }
                }
                assertThat(observed.get()).isExactlyInstanceOf(ErrorException.class).hasNoCause();
                assertThat(observed.get().getSuppressed()).isEmpty();
                assertThat(((ErrorException) observed.get()).getErrorCode())
                        .isSameAs(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
                assertThat(observed.get().getMessage()).contains(OUTGOING_ID);
                assertThat(probe.awaitAbort()).isTrue();
                assertThat(probe.tailBytesWritten()).isLessThan(probe.totalTailBytes());
                assertThat(simulator.requests).hasSize(1);
            });
        }
    }

    @Test
    void test_truncatedResponsesUseReceivedStatusWithoutRetry() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                for (int status : new int[]{200, 201, 301, 302, 307, 400, 503}) {
                    var probe = simulator.respondWithTruncationAfterPrefix(status,
                            "{}".getBytes(StandardCharsets.UTF_8));
                    var failure = new AtomicReference<Throwable>();
                    var completed = new CountDownLatch(1);
                    Thread requestThread = Thread.ofPlatform().name("vault-truncation-" + status).start(() -> {
                        MDC.put("requestId", OUTGOING_ID);
                        try {
                            failure.set(catchThrowable(() -> client.protect(command())));
                        } finally {
                            MDC.clear();
                            completed.countDown();
                        }
                    });
                    try {
                        assertThat(probe.awaitPrefix()).as("server prefix for status %s", status).isTrue();
                        assertThat(awaitBoundedRead(requestThread))
                                .as("client must capture status %s before truncation", status).isTrue();
                    } finally {
                        probe.truncate();
                        assertThat(completed.await(5, TimeUnit.SECONDS)).as("completion for status %s", status).isTrue();
                    }
                    if (status == 200) {
                        assertThat(failure.get()).as("truncated status %s", status)
                                .isExactlyInstanceOf(ServiceUnavailableException.class).hasNoCause();
                    } else {
                        assertThat(failure.get()).as("truncated status %s", status)
                                .isExactlyInstanceOf(ErrorException.class).hasNoCause();
                        assertThat(((ErrorException) failure.get()).getErrorCode())
                                .isSameAs(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
                    }
                    assertThat(failure.get().getSuppressed()).isEmpty();
                    assertThat(failure.get().getMessage()).contains(OUTGOING_ID);
                }
                assertThat(simulator.requests).hasSize(7);
            });
        }
    }

    @Test
    void test_timeoutAndTruncationAreUnavailableWithoutRetry() throws Exception {
        try (var simulator = new VaultSimulator()) {
            CountDownLatch gate = new CountDownLatch(1);
            simulator.respond(200, "{}".getBytes(StandardCharsets.UTF_8), false, gate);
            ClientContext.direct(simulator).withPropertyValues("vault.client.read-timeout=100ms").run(context -> {
                try {
                    assertThatThrownBy(() -> context.getBean(VaultClient.class).protect(command()))
                            .isExactlyInstanceOf(ServiceUnavailableException.class).hasNoCause();
                } finally { gate.countDown(); }
                assertThat(simulator.requests).hasSize(1);
            });
            simulator.respond(200, "{\"value\":\"x\"}".getBytes(StandardCharsets.UTF_8), true, null);
            ClientContext.direct(simulator).run(context -> assertThatThrownBy(() -> context.getBean(VaultClient.class)
                    .reveal(new RevealQuery(TOKEN, "P"))).isExactlyInstanceOf(ServiceUnavailableException.class).hasNoCause());
            assertThat(simulator.requests).hasSize(2);
        }
    }
}
