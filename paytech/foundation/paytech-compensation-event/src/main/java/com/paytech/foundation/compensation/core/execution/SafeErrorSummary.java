package com.paytech.foundation.compensation.core.execution;

import java.util.regex.Pattern;

/**
 * 生成有长度限制的诊断摘要，避免持久化任意异常消息。
 *
 * @author Codex
 */
public final class SafeErrorSummary {

    private static final int MAXIMUM_LENGTH = 1000;
    private static final String UNEXPECTED_SUMMARY = "Handler execution failed unexpectedly.";
    private static final Pattern SECRET_ASSIGNMENT =
        Pattern.compile("(?i)(password|secret|token|authorization|api[-_]?key|private[-_]?key|credential|cvv|cvc|pan)"
            + "\\s*[:=]\\s*(?:bearer\\s+)?[^,;\\r\\n]+");
    private static final Pattern BARE_BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern PREFIXED_OPAQUE_TOKEN =
        Pattern.compile("(?i)\\b(?:vault|vlt|token|tok|secret|credential|api[-_]?key)[_-][A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern LONG_DIGIT_SEQUENCE = Pattern.compile("(?<!\\d)\\d{12,19}(?!\\d)");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");

    public String unexpected(Throwable ignored) {
        return UNEXPECTED_SUMMARY;
    }

    public String explicit(String summary) {
        if (summary == null || summary.isBlank()) {
            return "Handler reported a failure without a safe summary.";
        }
        String redacted = SECRET_ASSIGNMENT.matcher(summary).replaceAll("$1=[REDACTED]");
        redacted = BARE_BEARER.matcher(redacted).replaceAll("Bearer [REDACTED]");
        redacted = PREFIXED_OPAQUE_TOKEN.matcher(redacted).replaceAll("[REDACTED_TOKEN]");
        redacted = LONG_DIGIT_SEQUENCE.matcher(redacted).replaceAll("[REDACTED_NUMBER]");
        redacted = CONTROL_CHARACTERS.matcher(redacted).replaceAll(" ");
        String normalized = REPEATED_WHITESPACE.matcher(redacted.trim()).replaceAll(" ");
        return normalized.length() <= MAXIMUM_LENGTH ? normalized : normalized.substring(0, MAXIMUM_LENGTH);
    }

    public String timeout() {
        return "Handler execution exceeded its configured timeout.";
    }
}
