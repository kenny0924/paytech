package com.paytech.vault.client.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.vault.client.VaultClientErrorCode;
import com.paytech.vault.client.internal.wire.ErrorResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 严格验证 Vault 新旧错误 Wire，并只向调用方保留本地安全错误与出站 Request ID。 */
public final class VaultResponseErrorHandler {
    private static final Set<String> PROBLEM_REQUIRED_MEMBERS = Set.of(
            "type", "title", "detail", "code", "requestId", "timestamp");
    private static final Set<String> PROBLEM_FORBIDDEN_MEMBERS = Set.of("status", "instance", "message");
    private static final Set<String> FIELD_ERROR_MEMBERS = Set.of("field", "message");
    private static final Set<String> LEGACY_MEMBERS = Set.of("code", "message", "requestId", "timestamp");
    private static final Pattern LEGACY_REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{1,64}");
    private static final Pattern LEGACY_TIMESTAMP_PATTERN = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,9})?Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private VaultResponseErrorHandler() {}

    public static RuntimeException failure(int status, JsonNode body, HttpHeaders headers, String requestId,
            boolean legacyErrorResponseEnabled) {
        VaultClientErrorCode code;
        try {
            WireFormat format = requireWireFormat(headers, legacyErrorResponseEnabled);
            code = format == WireFormat.PROBLEM
                    ? readProblem(status, body, headers)
                    : readLegacy(status, body, headers);
        } catch (RuntimeException ignored) {
            return invalidResponse(requestId);
        }
        return classifiedFailure(status, code, requestId);
    }

    public static ErrorException invalidResponse(String requestId) {
        return new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE,
                correlatedMessage(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, requestId));
    }

    public static ServiceUnavailableException unavailable(String requestId) {
        var code = VaultClientErrorCode.DEPENDENCY_UNAVAILABLE;
        return new ServiceUnavailableException(new CorrelatedCode(code, correlatedMessage(code, requestId)));
    }

    private static VaultClientErrorCode readProblem(int status, JsonNode body, HttpHeaders headers) {
        requireObject(body);
        validateUnicode(body);
        for (String member : PROBLEM_REQUIRED_MEMBERS) {
            requireNonBlankText(body, member);
        }
        for (String member : PROBLEM_FORBIDDEN_MEMBERS) {
            require(!body.has(member));
        }
        validateFieldErrors(body.get("errors"));

        ApiProblemResponse problem;
        try {
            problem = JSON.treeToValue(body, ApiProblemResponse.class);
        } catch (JacksonException invalid) {
            throw new IllegalArgumentException("invalid Problem object");
        }
        HttpStatus httpStatus = HttpStatus.resolve(status);
        require(httpStatus != null);
        String responseRequestId = requireRequestId(headers, true);
        ApiProblemProfile.validate(problem, httpStatus.getReasonPhrase(), responseRequestId);
        VaultClientErrorCode code = requireCode(problem.code());
        requireStatusCode(status, code);
        return code;
    }

    private static VaultClientErrorCode readLegacy(int status, JsonNode body, HttpHeaders headers) {
        requireObject(body);
        require(body.size() == LEGACY_MEMBERS.size()
                && Set.copyOf(body.propertyNames()).equals(LEGACY_MEMBERS));
        validateUnicode(body);
        for (String member : LEGACY_MEMBERS) {
            requireNonBlankText(body, member);
        }
        ErrorResponse error = new ErrorResponse(
                body.get("code").stringValue(),
                body.get("message").stringValue(),
                body.get("requestId").stringValue(),
                body.get("timestamp").stringValue());
        require(LEGACY_REQUEST_ID_PATTERN.matcher(error.requestId()).matches());
        require(isLegacyTimestamp(error.timestamp()));
        require(error.requestId().equals(requireRequestId(headers, false)));
        VaultClientErrorCode code = requireCode(error.code());
        requireStatusCode(status, code);
        return code;
    }

    private static RuntimeException classifiedFailure(int status, VaultClientErrorCode code, String requestId) {
        String message = correlatedMessage(code, requestId);
        if (status == 503) return new ServiceUnavailableException(new CorrelatedCode(code, message));
        if (status >= 400 && status < 500) return new FailException(code, message);
        return new ErrorException(code, message);
    }

    private static WireFormat requireWireFormat(HttpHeaders headers, boolean legacyErrorResponseEnabled) {
        List<String> values = headers.get(HttpHeaders.CONTENT_TYPE);
        require(values != null && values.size() == 1);
        String value = values.getFirst();
        require(!hasUnquotedComma(value) && hasUniqueParameters(value));
        MediaType mediaType = MediaType.parseMediaType(value);
        require(mediaType.getCharset() == null || StandardCharsets.UTF_8.equals(mediaType.getCharset()));
        String baseType = mediaType.getType() + "/" + mediaType.getSubtype();
        if (ApiProblemProfile.CONTENT_TYPE.equalsIgnoreCase(baseType)) return WireFormat.PROBLEM;
        if (legacyErrorResponseEnabled && MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(baseType)) {
            return WireFormat.LEGACY;
        }
        throw new IllegalArgumentException("unsupported error media type");
    }

    private static String requireRequestId(HttpHeaders headers, boolean problemProfile) {
        List<String> values = headers.get(ApiProblemProfile.REQUEST_ID_HEADER);
        require(values != null && values.size() == 1);
        String value = values.getFirst();
        require(problemProfile
                ? ApiProblemProfile.isValidRequestId(value)
                : value != null && LEGACY_REQUEST_ID_PATTERN.matcher(value).matches());
        return value;
    }

    private static void validateFieldErrors(JsonNode errors) {
        if (errors == null) return;
        require(errors.isArray() && errors.size() > 0);
        for (JsonNode error : errors) {
            require(error.isObject() && error.size() == FIELD_ERROR_MEMBERS.size()
                    && Set.copyOf(error.propertyNames()).equals(FIELD_ERROR_MEMBERS));
            requireNonBlankText(error, "field");
            requireNonBlankText(error, "message");
        }
    }

    private static void validateUnicode(JsonNode value) {
        if (value.isObject()) {
            for (String property : value.propertyNames()) {
                VaultValidation.unicode(property);
                validateUnicode(value.get(property));
            }
        } else if (value.isArray()) {
            value.forEach(VaultResponseErrorHandler::validateUnicode);
        } else if (value.isString()) {
            VaultValidation.unicode(value.stringValue());
        }
    }

    private static boolean isLegacyTimestamp(String value) {
        if (!LEGACY_TIMESTAMP_PATTERN.matcher(value).matches()) return false;
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }

    private static VaultClientErrorCode requireCode(String value) {
        for (VaultClientErrorCode code : VaultClientErrorCode.values()) {
            if (code.getCode().equals(value)) return code;
        }
        throw new IllegalArgumentException("unknown Vault error code");
    }

    private static void requireStatusCode(int status, VaultClientErrorCode code) {
        boolean valid = switch (status) {
            case 400 -> code == VaultClientErrorCode.INVALID_REQUEST
                    || code == VaultClientErrorCode.VALUE_TOO_LARGE
                    || code == VaultClientErrorCode.UNSUPPORTED_DATA_TYPE
                    || code == VaultClientErrorCode.TOKEN_MODE_NOT_ALLOWED;
            case 403 -> code == VaultClientErrorCode.ACCESS_DENIED;
            case 404 -> code == VaultClientErrorCode.TOKEN_NOT_FOUND
                    || code == VaultClientErrorCode.POLICY_ENTRY_NOT_FOUND;
            case 409 -> code == VaultClientErrorCode.IDEMPOTENCY_KEY_CONFLICT
                    || code == VaultClientErrorCode.POLICY_VERSION_CONFLICT
                    || code == VaultClientErrorCode.POLICY_ENTRY_EXISTS;
            case 500 -> code == VaultClientErrorCode.INTEGRITY_CHECK_FAILED
                    || code == VaultClientErrorCode.INTERNAL_ERROR;
            case 503 -> code == VaultClientErrorCode.DEPENDENCY_UNAVAILABLE
                    || code == VaultClientErrorCode.AUDIT_UNAVAILABLE;
            default -> false;
        };
        require(valid);
    }

    private static void requireObject(JsonNode body) {
        require(body != null && body.isObject());
    }

    private static void requireNonBlankText(JsonNode object, String member) {
        JsonNode value = object.get(member);
        require(value != null && value.isString() && !value.stringValue().isBlank());
    }

    private static boolean hasUnquotedComma(String value) {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (quoted && current == '\\') {
                escaped = true;
            } else if (current == '"') {
                quoted = !quoted;
            } else if (!quoted && current == ',') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUniqueParameters(String value) {
        Set<String> names = new HashSet<>();
        boolean quoted = false;
        boolean escaped = false;
        int segmentStart = -1;
        for (int index = 0; index <= value.length(); index++) {
            char current = index == value.length() ? ';' : value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (quoted && current == '\\') {
                escaped = true;
            } else if (current == '"') {
                quoted = !quoted;
            } else if (!quoted && current == ';') {
                if (segmentStart >= 0) {
                    String segment = value.substring(segmentStart, index).trim();
                    int equals = segment.indexOf('=');
                    if (equals <= 0 || !names.add(segment.substring(0, equals).trim().toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }
                segmentStart = index + 1;
            }
        }
        return true;
    }

    private static String correlatedMessage(CodeMessageEnum code, String requestId) {
        return code.getMessage() + "; requestId=" + requestId;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("invalid Vault error response");
    }

    private enum WireFormat {
        PROBLEM,

        LEGACY
    }

    private record CorrelatedCode(VaultClientErrorCode code, String message) implements CodeMessageEnum {
        @Override public String getCode() { return code.getCode(); }
        @Override public String getMessage() { return message; }
    }
}
