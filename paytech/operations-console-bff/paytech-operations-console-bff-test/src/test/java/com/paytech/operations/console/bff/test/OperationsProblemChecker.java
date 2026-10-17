package com.paytech.operations.console.bff.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Operations BFF 真实 HTTP 错误响应的统一 Problem Profile 门禁。 */
public final class OperationsProblemChecker {
    private static final Set<String> REQUIRED_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern TIMESTAMP =
        Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z$");
    private static final Pattern FIELD =
        Pattern.compile("^[A-Za-z][A-Za-z0-9]*(?:(?:\\.[A-Za-z][A-Za-z0-9]*)|(?:\\[\\d+]))*$");

    private OperationsProblemChecker() { }

    public static JsonNode assertProblem(JsonMapper json, HttpResponse<String> response, int status, String code) {
        return assertProblem(json, response, status, code, null);
    }

    public static JsonNode assertProblem(JsonMapper json, HttpResponse<String> response, int status, String code,
        String expectedRequestId) {
        String requestId = assertCommonHeaders(response, status, expectedRequestId);

        JsonNode body = json.readTree(response.body());
        assertTrue(body.isObject(), response.body());
        Set<String> expectedMembers = new HashSet<>(REQUIRED_MEMBERS);
        if (body.has("errors")) expectedMembers.add("errors");
        assertEquals(expectedMembers, body.propertyNames());
        assertEquals("about:blank", requiredText(body, "type"));
        assertEquals(HttpStatus.valueOf(status).getReasonPhrase(), requiredText(body, "title"));
        assertFalse(requiredText(body, "detail").isBlank());
        assertEquals(code, requiredText(body, "code"));
        assertEquals(requestId, requiredText(body, "requestId"));
        String timestamp = requiredText(body, "timestamp");
        assertTrue(TIMESTAMP.matcher(timestamp).matches(), timestamp);
        Instant.parse(timestamp);

        if (body.has("errors")) {
            assertEquals("VALIDATION_ERROR", code, "只有安全的请求体字段校验可以返回 errors");
            JsonNode errors = body.path("errors");
            assertTrue(errors.isArray() && !errors.isEmpty());
            for (JsonNode error : errors) {
                assertTrue(error.isObject());
                assertEquals(Set.of("field", "message"), error.propertyNames());
                assertTrue(FIELD.matcher(requiredText(error, "field")).matches());
                assertFalse(requiredText(error, "message").isBlank());
            }
        }
        return body;
    }

    public static void assertHeadProblem(HttpResponse<byte[]> response, int status, String expectedRequestId) {
        assertCommonHeaders(response, status, expectedRequestId);
        assertEquals(0, response.body().length, "HEAD Problem 不得传输响应 Body");
    }

    private static String assertCommonHeaders(HttpResponse<?> response, int status, String expectedRequestId) {
        assertEquals(status, response.statusCode(), String.valueOf(response.body()));
        assertEquals(List.of("application/problem+json"), response.headers().allValues("Content-Type"));
        List<String> dates = response.headers().allValues("Date");
        assertEquals(1, dates.size(), "真实 HTTP Problem 必须由容器添加一个 Date");
        assertDoesNotThrow(() -> ZonedDateTime.parse(dates.getFirst(), DateTimeFormatter.RFC_1123_DATE_TIME),
            "Date 必须是可解析的标准 HTTP 日期");
        assertTrue(response.headers().allValues("X-Timestamp").isEmpty());

        List<String> requestIds = response.headers().allValues("X-Request-Id");
        assertEquals(1, requestIds.size());
        String requestId = requestIds.getFirst();
        assertTrue(REQUEST_ID.matcher(requestId).matches(), requestId);
        if (expectedRequestId != null) assertEquals(expectedRequestId, requestId);
        return requestId;
    }

    private static String requiredText(JsonNode object, String member) {
        JsonNode value = object.path(member);
        assertTrue(value.isString(), member + " 必须是 JSON 字符串");
        return value.stringValue();
    }
}
