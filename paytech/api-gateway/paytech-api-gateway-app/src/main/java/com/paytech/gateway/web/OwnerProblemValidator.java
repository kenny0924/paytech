package com.paytech.gateway.web;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Strictly validates a first-party Owner error response without retaining or reporting Owner data. */
@Component
public final class OwnerProblemValidator {
    private static final Set<String> REQUIRED_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");
    private static final Set<String> FORBIDDEN_MEMBERS = Set.of("status", "instance", "message");
    private static final Set<String> FIELD_ERROR_MEMBERS = Set.of("field", "message");
    private static final Set<String> LEGACY_MEMBERS = Set.of("code", "message", "requestId", "timestamp");
    private static final Pattern LEGACY_REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern LEGACY_TIMESTAMP_PATTERN = Pattern.compile(
        "[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,9})?Z");
    private static final ObjectMapper STRICT_READER = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    public void validate(HttpStatusCode status, HttpHeaders headers, byte[] body, String trustedRequestId,
        boolean legacyAllowed) {
        try {
            requireErrorStatus(status);
            MediaKind mediaKind = requireMedia(headers, legacyAllowed);
            String ownerRequestId = requireOwnerRequestId(headers);
            require(ApiProblemProfile.isValidRequestId(trustedRequestId));
            require(ownerRequestId.equals(trustedRequestId));
            if (mediaKind == MediaKind.PROBLEM) {
                validateProblem(status, body, ownerRequestId);
            } else {
                validateLegacy(body, ownerRequestId, trustedRequestId);
            }
        } catch (GatewayUpstreamProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    public void validateHead(HttpStatusCode status, HttpHeaders headers, String trustedRequestId) {
        try {
            requireErrorStatus(status);
            require(requireMedia(headers, false) == MediaKind.PROBLEM);
            String ownerRequestId = requireOwnerRequestId(headers);
            require(ApiProblemProfile.isValidRequestId(trustedRequestId));
            require(ownerRequestId.equals(trustedRequestId));
        } catch (GatewayUpstreamProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    private void validateProblem(HttpStatusCode status, byte[] body, String ownerRequestId) {
        JsonNode root = readObject(body);
        FORBIDDEN_MEMBERS.forEach(member -> require(!root.has(member)));
        REQUIRED_MEMBERS.forEach(member -> require(isNonBlankText(root.get(member))));
        List<ProblemFieldError> errors = validateErrors(root.get("errors"));
        HttpStatus resolved = HttpStatus.resolve(status.value());
        require(resolved != null);
        ApiProblemResponse problem = new ApiProblemResponse(root.get("type").stringValue(),
            root.get("title").stringValue(), root.get("detail").stringValue(), root.get("code").stringValue(),
            root.get("requestId").stringValue(), root.get("timestamp").stringValue(), errors);
        ApiProblemProfile.validate(problem, resolved.getReasonPhrase(), ownerRequestId);
    }

    private void validateLegacy(byte[] body, String ownerRequestId, String trustedRequestId) {
        JsonNode root = readObject(body);
        require(root.size() == LEGACY_MEMBERS.size());
        LEGACY_MEMBERS.forEach(member -> require(isNonBlankText(root.get(member))));
        String bodyRequestId = root.get("requestId").stringValue();
        require(LEGACY_REQUEST_ID_PATTERN.matcher(bodyRequestId).matches());
        require(bodyRequestId.equals(ownerRequestId));
        require(bodyRequestId.equals(trustedRequestId));
        require(isLegacyTimestamp(root.get("timestamp").stringValue()));
    }

    private List<ProblemFieldError> validateErrors(JsonNode errorsNode) {
        if (errorsNode == null) {
            return null;
        }
        require(errorsNode.isArray() && !errorsNode.isEmpty());
        List<ProblemFieldError> errors = new ArrayList<>();
        for (JsonNode error : errorsNode) {
            require(error.isObject() && error.size() == FIELD_ERROR_MEMBERS.size());
            require(isNonBlankText(error.get("field")) && isNonBlankText(error.get("message")));
            errors.add(new ProblemFieldError(error.get("field").stringValue(), error.get("message").stringValue()));
        }
        return List.copyOf(errors);
    }

    private JsonNode readObject(byte[] body) {
        require(body != null && body.length > 0);
        try {
            JsonNode root = STRICT_READER.readTree(decodeUtf8(body));
            require(root != null && root.isObject());
            return root;
        } catch (JacksonException failure) {
            throw invalid();
        }
    }

    private String decodeUtf8(byte[] body) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException failure) {
            throw invalid();
        }
    }

    private MediaKind requireMedia(HttpHeaders headers, boolean legacyAllowed) {
        List<String> rawValues = headers.get(HttpHeaders.CONTENT_TYPE);
        require(rawValues != null && rawValues.size() == 1);
        String rawValue = rawValues.getFirst();
        require(rawValue != null && !rawValue.isBlank() && rawValue.indexOf(',') < 0);
        MediaType mediaType = MediaType.parseMediaType(rawValue);
        long parameterDelimiters = rawValue.chars().filter(character -> character == ';').count();
        require(parameterDelimiters == mediaType.getParameters().size());
        String base = mediaType.getType() + "/" + mediaType.getSubtype();
        if (ApiProblemProfile.CONTENT_TYPE.equalsIgnoreCase(base)) {
            requireProblemParameters(mediaType);
            return MediaKind.PROBLEM;
        }
        require(legacyAllowed && MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(base)
            && mediaType.getParameters().isEmpty());
        return MediaKind.LEGACY;
    }

    private void requireProblemParameters(MediaType mediaType) {
        if (mediaType.getParameters().isEmpty()) {
            return;
        }
        require(mediaType.getParameters().size() == 1);
        String charsetName = mediaType.getParameters().entrySet().stream()
            .filter(entry -> "charset".equalsIgnoreCase(entry.getKey())).map(java.util.Map.Entry::getValue)
            .findFirst().orElse(null);
        require(charsetName != null);
        require(StandardCharsets.UTF_8.equals(java.nio.charset.Charset.forName(charsetName)));
    }

    private String requireOwnerRequestId(HttpHeaders headers) {
        List<String> values = headers.get(ApiProblemProfile.REQUEST_ID_HEADER);
        require(values != null && values.size() == 1);
        String value = values.getFirst();
        require(ApiProblemProfile.isValidRequestId(value));
        return value;
    }

    private void requireErrorStatus(HttpStatusCode status) {
        require(status != null && status.isError());
    }

    private boolean isLegacyTimestamp(String value) {
        if (value == null || !LEGACY_TIMESTAMP_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException failure) {
            return false;
        }
    }

    private boolean isNonBlankText(JsonNode value) {
        return value != null && value.isString() && !value.stringValue().isBlank();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private GatewayUpstreamProtocolException invalid() {
        return new GatewayUpstreamProtocolException();
    }

    private enum MediaKind {
        PROBLEM,

        LEGACY
    }
}
