package com.paytech.gateway.auto.security;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

/** 所有故障通过真实 PLT-I03 HTTP 连接注入，不替换认证 Manager 或 Client。 */
class PlatformCredentialValidationClientAutoTest extends ClientApiGatewayAutoTestBase {
    private static final String VALID = """
        {"tenantId":"810000001","tenantType":"CLIENT","ownerRefId":"820000001",
         "credentialId":"830000001","effectiveScopeCodes":["orders.write"]}
        """;

    @ParameterizedTest(name = "protocol failure {index}")
    @MethodSource("protocolFailures")
    void test_invalidPlatformResponseFailsClosedWithoutRetry(int status, String body, Duration delay, boolean disconnect) {
        GatewayAutoTestRuntime.credentialSimulator().expectRaw(status, body, delay, disconnect);
        var result = authenticatedRequest().exchange().expectBody().returnResult();
        assertProblem(result, 503, "DEPENDENCY_UNAVAILABLE");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
        assertFalse(new String(result.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8)
            .contains(PlatformCredentialValidationSimulator.CREDENTIAL));
    }

    static Stream<Arguments> protocolFailures() {
        Stream<Arguments> malformed = Stream.of("", "null", "[]", "{", VALID + "{}",
            VALID.replace("\"CLIENT\"", "\"PARTNER\""),
            VALID.replace("\"tenantId\":\"810000001\"", "\"tenantId\":810000001"),
            VALID.replace("\"tenantId\":\"810000001\"", "\"tenantId\":\"810000001\",\"tenantId\":\"9\""),
            VALID.replace("\"820000001\"", "null"), VALID.replace("\"830000001\"", "\"0\""),
            VALID.replace("[\"orders.write\"]", "null"),
            VALID.replace("[\"orders.write\"]", "[null]"),
            VALID.replace("orders.write", "orders.*"), VALID.replace("orders.write", "Orders.write"),
            VALID.replace("[\"orders.write\"]", "[" + String.join(",", java.util.Collections.nCopies(
                ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES + 1, "\"orders.write\"")) + "]"),
            VALID.replace("orders.write", "x".repeat(ApiCredentialConstraints.MAX_SCOPE_CODE_LENGTH + 1)),
            VALID + " ".repeat(20_000))
            .map(body -> Arguments.of(200, body, Duration.ZERO, false));
        return Stream.concat(malformed, Stream.of(
            Arguments.of(200, VALID, Duration.ofSeconds(3), false),
            Arguments.of(200, VALID, Duration.ZERO, true),
            Arguments.of(500, "{}", Duration.ZERO, false), Arguments.of(503, "{}", Duration.ZERO, false),
            Arguments.of(302, "{}", Duration.ZERO, false), Arguments.of(204, "", Duration.ZERO, false),
            Arguments.of(401, "{}", Duration.ZERO, false), Arguments.of(403, "{}", Duration.ZERO, false)));
    }

    @ParameterizedTest(name = "Platform media type {index}")
    @MethodSource("invalidContentTypes")
    void test_invalidContentTypeIsDependencyFailure(List<String> values) {
        GatewayAutoTestRuntime.credentialSimulator().expectMedia(VALID, values);
        assertProblem(authenticatedRequest().exchange().expectBody().returnResult(), 503, "DEPENDENCY_UNAVAILABLE");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    static Stream<List<String>> invalidContentTypes() {
        return Stream.of(List.of(), List.of("application/*"), List.of("*/*"), List.of("not a media type"),
            List.of("text/plain"), List.of("application/json", "application/json"));
    }

    @ParameterizedTest(name = "authorization grammar {index}")
    @MethodSource("invalidAuthorization")
    void test_invalidAuthorizationNeverCallsPlatform(List<String> values) {
        var result = client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .headers(headers -> headers.put(HttpHeaders.AUTHORIZATION, values)).exchange().expectBody().returnResult();
        assertProblem(result, 401, "UNAUTHENTICATED");
        assertEquals(0, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    static Stream<List<String>> invalidAuthorization() {
        String key = PlatformCredentialValidationSimulator.CREDENTIAL;
        return Stream.of(List.of(), List.of("Basic " + key), List.of("Bearer"), List.of("Bearer "),
            List.of("Bearer  " + key), List.of("Bearer " + key + " extra"), List.of("Bearer " + key + "," + key),
            List.of("Bearer " + key, "Bearer " + key), List.of("Bearer testkey1." + "a".repeat(31)),
            List.of("Bearer " + "p".repeat(33) + "." + "a".repeat(32)),
            List.of("Bearer testkey1." + "a".repeat(ApiCredentialConstraints.MAX_SECRET_LENGTH + 1)));
    }

    @Test
    void test_lowerCaseBearerAndEmptyScopeAreAuthenticatedButForbidden() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid();
        var result = client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "bEaReR " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .exchange().expectBody().returnResult();
        assertProblem(result, 403, "INSUFFICIENT_SCOPE");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }
}
