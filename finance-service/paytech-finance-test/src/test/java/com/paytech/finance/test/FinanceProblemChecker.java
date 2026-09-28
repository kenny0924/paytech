package com.paytech.finance.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Finance 真实 HTTP 错误响应的统一 Problem Profile 门禁。 */
public final class FinanceProblemChecker {

    private static final Set<String> PROBLEM_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern TIMESTAMP =
        Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z$");

    private FinanceProblemChecker() { }

    public static JsonNode assertProblem(JsonMapper json, HttpResponse<String> response, int status, String code,
        String detail, String expectedRequestId) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(List.of("application/problem+json"), response.headers().allValues("Content-Type"));
        List<String> dates = response.headers().allValues("Date");
        assertEquals(1, dates.size());
        ZonedDateTime.parse(dates.getFirst(), DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(response.headers().allValues("X-Timestamp").isEmpty());

        List<String> requestIds = response.headers().allValues("X-Request-Id");
        assertEquals(1, requestIds.size());
        String requestId = requestIds.getFirst();
        assertTrue(REQUEST_ID.matcher(requestId).matches(), requestId);
        assertEquals(expectedRequestId, requestId);

        JsonNode body = json.readTree(response.body());
        assertEquals(PROBLEM_MEMBERS, body.propertyNames());
        assertEquals("about:blank", body.path("type").asString());
        assertEquals(HttpStatus.valueOf(status).getReasonPhrase(), body.path("title").asString());
        assertEquals(detail, body.path("detail").asString());
        assertFalse(body.path("detail").asString().isBlank());
        assertEquals(code, body.path("code").asString());
        assertEquals(requestId, body.path("requestId").asString());
        String timestamp = body.path("timestamp").asString();
        assertTrue(TIMESTAMP.matcher(timestamp).matches(), timestamp);
        Instant.parse(timestamp);
        assertFalse(body.has("status"));
        assertFalse(body.has("instance"));
        assertFalse(body.has("message"));
        assertFalse(body.has("errors"));
        return body;
    }
}
