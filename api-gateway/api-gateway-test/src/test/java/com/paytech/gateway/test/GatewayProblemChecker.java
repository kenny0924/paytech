package com.paytech.gateway.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 Gateway HTTP 错误响应的统一 Problem Profile 门禁。 */
public final class GatewayProblemChecker {
    private static final Set<String> PROBLEM_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GatewayProblemChecker() {}

    public static EntityExchangeResult<byte[]> assertProblem(WebTestClient.ResponseSpec response, int status,
        String code) {
        EntityExchangeResult<byte[]> result = response.expectStatus().isEqualTo(status).expectBody().returnResult();
        return assertProblem(result, status, code);
    }

    public static EntityExchangeResult<byte[]> assertProblem(EntityExchangeResult<byte[]> result, int status,
        String code) {
        assertCommonHeaders(result, status);
        byte[] bytes = result.getResponseBody();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        JsonNode body = JSON.readTree(bytes);
        assertEquals(PROBLEM_MEMBERS, body.propertyNames());
        for (String member : PROBLEM_MEMBERS) {
            assertTrue(body.get(member).isString(), member);
            assertFalse(body.get(member).stringValue().isBlank(), member);
        }
        assertEquals(ApiProblemProfile.TYPE, body.get("type").stringValue());
        assertEquals(HttpStatus.valueOf(status).getReasonPhrase(), body.get("title").stringValue());
        assertEquals(code, body.get("code").stringValue());
        assertEquals(result.getResponseHeaders().getFirst(ApiProblemProfile.REQUEST_ID_HEADER),
            body.get("requestId").stringValue());
        assertTrue(ApiProblemProfile.isCanonicalTimestamp(body.get("timestamp").stringValue()));
        return result;
    }

    public static EntityExchangeResult<byte[]> assertHeadProblem(WebTestClient.ResponseSpec response, int status) {
        EntityExchangeResult<byte[]> result = response.expectStatus().isEqualTo(status).expectBody().returnResult();
        assertCommonHeaders(result, status);
        byte[] body = result.getResponseBody();
        assertTrue(body == null || body.length == 0);
        return result;
    }

    private static void assertCommonHeaders(EntityExchangeResult<?> result, int status) {
        assertEquals(status, result.getStatus().value());
        HttpHeaders headers = result.getResponseHeaders();
        assertEquals(List.of(ApiProblemProfile.CONTENT_TYPE), headers.getOrEmpty(HttpHeaders.CONTENT_TYPE));
        List<String> requestIds = headers.getOrEmpty(ApiProblemProfile.REQUEST_ID_HEADER);
        assertEquals(1, requestIds.size());
        assertTrue(ApiProblemProfile.isValidRequestId(requestIds.getFirst()));
        List<String> dates = headers.getOrEmpty(HttpHeaders.DATE);
        assertEquals(1, dates.size());
        ZonedDateTime.parse(dates.getFirst(), DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(headers.getOrEmpty("X-Timestamp").isEmpty());
    }
}
