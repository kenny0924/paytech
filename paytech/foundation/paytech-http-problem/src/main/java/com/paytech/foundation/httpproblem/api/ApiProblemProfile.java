package com.paytech.foundation.httpproblem.api;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.regex.Pattern;

import com.paytech.foundation.common.http.ApiHeaderNames;

public final class ApiProblemProfile {

    public static final String TYPE = "about:blank";

    public static final String CONTENT_TYPE = "application/problem+json";

    public static final String REQUEST_ID_HEADER = ApiHeaderNames.REQUEST_ID;

    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile(
        "[A-Za-z0-9][A-Za-z0-9._-]{0,63}"
    );

    private static final Pattern CANONICAL_TIMESTAMP_PATTERN = Pattern.compile(
        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"
    );

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
        .appendLiteral('Z')
        .toFormatter()
        .withZone(ZoneOffset.UTC);

    private ApiProblemProfile() {
    }

    public static boolean isValidRequestId(String value) {
        return value != null && REQUEST_ID_PATTERN.matcher(value).matches();
    }

    public static String formatTimestamp(Instant value) {
        return TIMESTAMP_FORMATTER.format(Objects.requireNonNull(value, "value must not be null"));
    }

    public static boolean isCanonicalTimestamp(String value) {
        if (value == null || !CANONICAL_TIMESTAMP_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            return formatTimestamp(Instant.parse(value)).equals(value);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    public static void validate(ApiProblemResponse value, String expectedTitle, String headerRequestId) {
        require(value != null, "Problem response must not be null");
        require(TYPE.equals(value.type()), "Problem type must be about:blank");
        require(isNonBlank(expectedTitle), "Expected title must not be blank");
        require(expectedTitle.equals(value.title()), "Problem title does not match the HTTP status");
        require(isNonBlank(value.detail()), "Problem detail must not be blank");
        require(isNonBlank(value.code()), "Problem code must not be blank");
        require(isValidRequestId(headerRequestId), "Request ID header is invalid");
        require(headerRequestId.equals(value.requestId()), "Problem request ID does not match the header");
        require(isCanonicalTimestamp(value.timestamp()), "Problem timestamp is not canonical UTC RFC 3339");
        validateFieldErrors(value.errors());
    }

    private static void validateFieldErrors(java.util.List<ProblemFieldError> errors) {
        if (errors == null) {
            return;
        }
        require(!errors.isEmpty(), "Problem field errors must not be empty");
        for (ProblemFieldError error : errors) {
            require(error != null, "Problem field error must not be null");
            require(isNonBlank(error.field()), "Problem field error field must not be blank");
            require(isNonBlank(error.message()), "Problem field error message must not be blank");
        }
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
