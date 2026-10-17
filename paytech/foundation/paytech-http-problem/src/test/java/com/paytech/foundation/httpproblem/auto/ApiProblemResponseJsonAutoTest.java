package com.paytech.foundation.httpproblem.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiProblemResponseJsonAutoTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void test_serializesExactlySixRequiredMembersWhenFieldErrorsAreAbsent() throws Exception {
        ApiProblemResponse response = new ApiProblemResponse(
            "about:blank",
            "Bad Request",
            "The request is invalid.",
            "INVALID_REQUEST",
            "request-01",
            "2026-09-06T10:20:30.123456Z",
            null
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals(6, json.size());
        assertTrue(json.has("type"));
        assertTrue(json.has("title"));
        assertTrue(json.has("detail"));
        assertTrue(json.has("code"));
        assertTrue(json.has("requestId"));
        assertTrue(json.has("timestamp"));
        assertFalse(json.has("errors"));
        assertFalse(json.has("status"));
        assertFalse(json.has("instance"));
        assertFalse(json.has("message"));
    }

    @Test
    void test_serializesNonEmptyFieldErrorsWithOnlyFieldAndMessage() throws Exception {
        ApiProblemResponse response = new ApiProblemResponse(
            "about:blank",
            "Unprocessable Content",
            "One or more request fields are invalid.",
            "VALIDATION_ERROR",
            "request-02",
            "2026-09-06T10:20:30.123456Z",
            List.of(new ProblemFieldError("items[0].unitAmount", "Must be non-negative."))
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals(7, json.size());
        assertEquals(1, json.path("errors").size());
        assertEquals(2, json.path("errors").get(0).size());
        assertEquals("items[0].unitAmount", json.path("errors").get(0).path("field").asString());
        assertEquals("Must be non-negative.", json.path("errors").get(0).path("message").asString());
    }

    @Test
    void test_omitsEmptyFieldErrors() throws Exception {
        ApiProblemResponse response = new ApiProblemResponse(
            "about:blank",
            "Bad Request",
            "The request is invalid.",
            "INVALID_REQUEST",
            "request-03",
            "2026-09-06T10:20:30.123456Z",
            List.of()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertFalse(json.has("errors"));
    }

    @Test
    void test_deserializesUnknownExtensionsForForwardCompatibility() throws Exception {
        String json = """
            {
              "type":"about:blank",
              "title":"Bad Request",
              "detail":"The request is invalid.",
              "code":"INVALID_REQUEST",
              "requestId":"request-04",
              "timestamp":"2026-09-06T10:20:30.123456Z",
              "futureDiagnostic":"ignored"
            }
            """;

        ApiProblemResponse response = objectMapper.readValue(json, ApiProblemResponse.class);

        assertEquals("request-04", response.requestId());
    }
}
