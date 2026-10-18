package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

/** Problem Checker 自身的严格 Wire 类型与标准 Header 反例。 */
class OperationsProblemCheckerAutoTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DATE = "Sun, 6 Sep 2026 10:20:30 GMT";

    @ParameterizedTest
    @MethodSource("nonTextualProblems")
    void test_checkerRejectsNonTextualProblemMembers(String body, int status, String code, String requestId) {
        HttpResponse<String> response = response(status, requestId, DATE, body);

        assertThrows(AssertionError.class, () -> assertProblem(JSON, response, status, code));
    }

    @Test
    void test_checkerRejectsUnparseableDate() {
        HttpResponse<String> response = response(400, "request-10001", "not-an-http-date", """
            {"type":"about:blank","title":"Bad Request","detail":"请求参数不正确",
             "code":"INVALID_REQUEST","requestId":"request-10001","timestamp":"2026-09-06T10:20:30.123456Z"}
            """);

        assertThrows(AssertionError.class, () -> assertProblem(JSON, response, 400, "INVALID_REQUEST"));
    }

    private static Stream<Arguments> nonTextualProblems() {
        return Stream.of(
            Arguments.of("""
                {"type":"about:blank","title":"Bad Request","detail":123,
                 "code":"INVALID_REQUEST","requestId":"request-10001","timestamp":"2026-09-06T10:20:30.123456Z"}
                """, 400, "INVALID_REQUEST", "request-10001"),
            Arguments.of("""
                {"type":"about:blank","title":"Bad Request","detail":"请求参数不正确",
                 "code":"INVALID_REQUEST","requestId":123,"timestamp":"2026-09-06T10:20:30.123456Z"}
                """, 400, "INVALID_REQUEST", "123"),
            Arguments.of("""
                {"type":"about:blank","title":"Unprocessable Entity","detail":"请求参数不正确",
                 "code":"VALIDATION_ERROR","requestId":"request-10001","timestamp":"2026-09-06T10:20:30.123456Z",
                 "errors":[{"field":true,"message":"不能为空"}]}
                """, 422, "VALIDATION_ERROR", "request-10001"),
            Arguments.of("""
                {"type":"about:blank","title":"Unprocessable Entity","detail":"请求参数不正确",
                 "code":"VALIDATION_ERROR","requestId":"request-10001","timestamp":"2026-09-06T10:20:30.123456Z",
                 "errors":[{"field":"amount","message":123}]}
                """, 422, "VALIDATION_ERROR", "request-10001"));
    }

    private static HttpResponse<String> response(int status, String requestId, String date, String body) {
        HttpHeaders headers = HttpHeaders.of(Map.of(
            "Content-Type", List.of("application/problem+json"),
            "Date", List.of(date),
            "X-Request-Id", List.of(requestId)), (name, value) -> true);
        return new StubResponse(status, headers, body);
    }

    private record StubResponse(int statusCode, HttpHeaders headers, String body) implements HttpResponse<String> {

        private static final URI URI_VALUE = URI.create("http://127.0.0.1/problem");
        private static final HttpRequest REQUEST = HttpRequest.newBuilder(URI_VALUE).GET().build();

        @Override
        public HttpRequest request() {
            return REQUEST;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI_VALUE;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
