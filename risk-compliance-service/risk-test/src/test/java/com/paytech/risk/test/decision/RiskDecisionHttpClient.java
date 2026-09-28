package com.paytech.risk.test.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 从随机 Loopback 端口调用 Risk Decision 真实 HTTP 入口。 */
public final class RiskDecisionHttpClient implements AutoCloseable {

    private static final Set<String> REQUIRED_PROBLEM_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern TIMESTAMP =
        Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z$");
    private static final Pattern FIELD =
        Pattern.compile("^[A-Za-z][A-Za-z0-9]*(?:(?:\\.[A-Za-z][A-Za-z0-9]*)|(?:\\[\\d+]))*$");
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final HttpClient client;

    public RiskDecisionHttpClient(int port) {
        baseUrl = "http://127.0.0.1:" + port;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public HttpResponse<String> get(String path, String actorId, String sessionId)
        throws IOException, InterruptedException {
        return client.send(request(path, actorId, sessionId).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(
        String path,
        String actorId,
        String sessionId,
        String idempotencyKey,
        Object body
    ) throws IOException, InterruptedException {
        HttpRequest request = request(path, actorId, sessionId)
            .header("Idempotency-Key", idempotencyKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 携带原始幂等身份调用真实整体删除入口。 */
    public HttpResponse<String> delete(String path, String actorId, String sessionId,
        String idempotencyKey, Object body) throws IOException, InterruptedException {
        HttpRequest request = request(path, actorId, sessionId)
            .header("Idempotency-Key", idempotencyKey).header("Content-Type", "application/json")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 决策流整体删除使用 Query CAS，不发送 JSON Body。 */
    public HttpResponse<String> deleteFlow(String path, String actorId, String sessionId, String key)
        throws IOException, InterruptedException {
        return client.send(request(path, actorId, sessionId).header("Idempotency-Key", key).DELETE().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** 字典没有幂等 Header；原规则命令仍使用原来的 post/delete 入口。 */
    public HttpResponse<String> dictionary(String method, String path, String actorId, String sessionId, String body)
        throws IOException, InterruptedException {
        return configuration(method, path, actorId, sessionId, body);
    }

    /** 变量和字典使用根版本 CAS，传输不添加幂等键。 */
    public HttpResponse<String> configuration(String method, String path, String actorId, String sessionId, String body)
        throws IOException, InterruptedException {
        return client.send(request(path, actorId, sessionId).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    public <T> T body(HttpResponse<String> response, Class<T> type) {
        return JSON.readValue(response.body(), type);
    }

    public JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    public static JsonNode assertProblem(HttpResponse<String> response, int status, String code) {
        return assertProblem(response, status, code, null);
    }

    public static JsonNode assertProblem(
        HttpResponse<String> response,
        int status,
        String code,
        String expectedRequestId
    ) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(List.of("application/problem+json"), response.headers().allValues("Content-Type"));
        List<String> dates = response.headers().allValues("Date");
        assertEquals(1, dates.size(), "真实 HTTP Problem 必须由容器添加一个 Date");
        ZonedDateTime.parse(dates.getFirst(), DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(response.headers().allValues("X-Timestamp").isEmpty());

        List<String> requestIds = response.headers().allValues("X-Request-Id");
        assertEquals(1, requestIds.size());
        String requestId = requestIds.getFirst();
        assertTrue(REQUEST_ID.matcher(requestId).matches(), requestId);
        if (expectedRequestId != null) {
            assertEquals(expectedRequestId, requestId);
        }

        JsonNode body = JSON.readTree(response.body());
        assertTrue(body.isObject(), response.body());
        Set<String> expectedMembers = new HashSet<>(REQUIRED_PROBLEM_MEMBERS);
        if (body.has("errors")) {
            expectedMembers.add("errors");
        }
        assertEquals(expectedMembers, body.propertyNames());
        for (String member : REQUIRED_PROBLEM_MEMBERS) {
            assertTrue(body.get(member).isString(), member);
            assertFalse(body.get(member).stringValue().isBlank(), member);
        }
        assertEquals("about:blank", body.get("type").stringValue());
        assertEquals(HttpStatus.valueOf(status).getReasonPhrase(), body.get("title").stringValue());
        assertEquals(code, body.get("code").stringValue());
        assertEquals(requestId, body.get("requestId").stringValue());
        String timestamp = body.get("timestamp").stringValue();
        assertTrue(TIMESTAMP.matcher(timestamp).matches(), timestamp);
        Instant.parse(timestamp);
        assertFalse(body.has("status"));
        assertFalse(body.has("instance"));
        assertFalse(body.has("message"));

        if (body.has("errors")) {
            assertEquals("VALIDATION_ERROR", code, "只有安全的请求体字段校验可以返回 errors");
            JsonNode errors = body.path("errors");
            assertTrue(errors.isArray() && !errors.isEmpty());
            for (JsonNode error : errors) {
                assertTrue(error.isObject());
                assertEquals(Set.of("field", "message"), error.propertyNames());
                assertTrue(error.get("field").isString());
                assertTrue(error.get("message").isString());
                assertTrue(FIELD.matcher(error.get("field").stringValue()).matches());
                assertFalse(error.get("message").stringValue().isBlank());
            }
        }
        return body;
    }

    private HttpRequest.Builder request(String path, String actorId, String sessionId) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("X-Gateway-Actor-Subject", actorId)
            .header("X-Gateway-Session-Id", sessionId)
            .header("Accept", "application/json");
    }

    @Override
    public void close() {
        client.close();
    }
}
