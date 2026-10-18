package com.paytech.foundation.httpclient.core.error;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Strict, safe decoder for first-party Problem responses. @author Codex */
final class HttpProblemDecoder {

    private static final Set<String> REQUIRED_MEMBERS = Set.of(
        "type", "title", "detail", "code", "requestId", "timestamp"
    );
    private static final Set<String> FORBIDDEN_MEMBERS = Set.of("status", "instance", "message");
    private static final Set<String> FIELD_ERROR_MEMBERS = Set.of("field", "message");
    private static final Set<String> LEGACY_MEMBERS = Set.of("code", "message", "requestId", "timestamp");
    private static final Pattern LEGACY_REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern LEGACY_TIMESTAMP_PATTERN = Pattern.compile(
        "[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,9})?Z"
    );

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    DecodedProblem read(RestClientResponseException failure, boolean legacyErrorResponseEnabled) {
        requireProblemOrLegacyContentType(failure.getResponseHeaders(), legacyErrorResponseEnabled);
        if (hasProblemContentType(failure.getResponseHeaders())) {
            return readProblem(failure);
        }
        return new DecodedProblem(readLegacy(failure), List.of(), false);
    }

    /** 已校验的 Problem 摘要；errors 为去重后的远端字段错误（field 与 message），旧格式恒为空。 */
    record DecodedProblem(String code, List<ProblemFieldError> errors, boolean strictProfile) {

        /** 去重后的字段路径。 */
        List<String> fields() {
            return errors.stream().map(ProblemFieldError::field).distinct().toList();
        }
    }

    void validateHead(RestClientResponseException failure) {
        if (!failure.getStatusCode().is4xxClientError() && !failure.getStatusCode().is5xxServerError()) {
            throw new IllegalArgumentException("HEAD error response status must be 4xx or 5xx");
        }
        requireProblemContentType(failure.getResponseHeaders());
        requireRequestId(failure.getResponseHeaders());
    }

    private DecodedProblem readProblem(RestClientResponseException failure) {
        String requestId = requireRequestId(failure.getResponseHeaders());
        JsonNode root = readStrictObject(failure.getResponseBodyAsString());
        rejectForbiddenMembers(root);
        requireRequiredTextMembers(root);
        validateFieldErrors(root.path("errors"));
        ApiProblemResponse problem;
        try {
            problem = objectMapper.treeToValue(root, ApiProblemResponse.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Problem response is not decodable", exception);
        }
        HttpStatus status = HttpStatus.resolve(failure.getStatusCode().value());
        if (status == null) {
            throw new IllegalArgumentException("Problem response status has no standard title");
        }
        ApiProblemProfile.validate(problem, status.getReasonPhrase(), requestId);
        return new DecodedProblem(problem.code(), problem.errors() == null ? List.of()
            : problem.errors().stream().distinct().toList(), true);
    }

    private String readLegacy(RestClientResponseException failure) {
        JsonNode root = readStrictObject(failure.getResponseBodyAsString());
        if (root.size() != LEGACY_MEMBERS.size() || !isText(root, "code") || !isText(root, "message")
            || !isText(root, "requestId") || !isText(root, "timestamp")
            || !isLegacyRequestId(root.path("requestId").stringValue())
            || !isLegacyInstant(root.path("timestamp").stringValue())) {
            throw new IllegalArgumentException("Legacy error response has invalid required members");
        }
        return root.path("code").stringValue();
    }

    private JsonNode readStrictObject(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Error response body must not be empty");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Error response body must be a JSON object");
            }
            return root;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Error response body is invalid JSON");
        }
    }

    private static void requireProblemOrLegacyContentType(HttpHeaders headers, boolean legacyErrorResponseEnabled) {
        if (hasProblemContentType(headers)) {
            return;
        }
        if (legacyErrorResponseEnabled && hasLegacyContentType(headers)) {
            return;
        }
        throw new IllegalArgumentException("Error response content type is not permitted");
    }

    private static void requireProblemContentType(HttpHeaders headers) {
        if (!hasProblemContentType(headers)) {
            throw new IllegalArgumentException("Error response content type must be application/problem+json");
        }
    }

    private static boolean hasProblemContentType(HttpHeaders headers) {
        return hasExactMediaType(headers, ApiProblemProfile.CONTENT_TYPE);
    }

    private static boolean hasLegacyContentType(HttpHeaders headers) {
        return hasExactMediaType(headers, MediaType.APPLICATION_JSON_VALUE);
    }

    private static boolean hasExactMediaType(HttpHeaders headers, String expected) {
        List<String> contentTypes = headers.get(HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null || contentTypes.size() != 1) {
            return false;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentTypes.getFirst());
            return expected.equals(parsed.getType() + "/" + parsed.getSubtype());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String requireRequestId(HttpHeaders headers) {
        List<String> values = headers.get(ApiProblemProfile.REQUEST_ID_HEADER);
        if (values == null || values.size() != 1 || !ApiProblemProfile.isValidRequestId(values.getFirst())) {
            throw new IllegalArgumentException("Error response request ID header is invalid");
        }
        return values.getFirst();
    }

    private static void rejectForbiddenMembers(JsonNode root) {
        for (String member : FORBIDDEN_MEMBERS) {
            if (root.has(member)) {
                throw new IllegalArgumentException("Problem response contains a forbidden member");
            }
        }
    }

    private static void requireRequiredTextMembers(JsonNode root) {
        for (String member : REQUIRED_MEMBERS) {
            if (!isText(root, member)) {
                throw new IllegalArgumentException("Problem response has an invalid required member");
            }
        }
    }

    private static void validateFieldErrors(JsonNode errors) {
        if (errors.isMissingNode()) {
            return;
        }
        if (errors.isNull() || !errors.isArray() || errors.size() == 0) {
            throw new IllegalArgumentException("Problem field errors must be a non-empty array");
        }
        for (JsonNode error : errors) {
            if (!error.isObject() || error.size() != FIELD_ERROR_MEMBERS.size() || !isText(error, "field")
                || !isText(error, "message")) {
                throw new IllegalArgumentException("Problem field error has an invalid shape");
            }
        }
    }

    private static boolean isText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isString() && !value.stringValue().isBlank();
    }

    private static boolean isLegacyInstant(String value) {
        if (value == null || !LEGACY_TIMESTAMP_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }

    private static boolean isLegacyRequestId(String value) {
        return value != null && LEGACY_REQUEST_ID_PATTERN.matcher(value).matches();
    }
}
