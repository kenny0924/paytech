package com.paytech.risk.decision.domain.rule;

import com.paytech.foundation.common.error.InvalidParameterException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical UTC timestamp with an exact six-digit microsecond fraction. */
public final class CanonicalUtcMicrosecond {

    private static final Pattern SHAPE = Pattern.compile(
        "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z");
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseSensitive()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    private CanonicalUtcMicrosecond() {
    }

    public static String normalize(String raw) {
        try {
            if (raw == null || !SHAPE.matcher(raw).matches()
                || Integer.parseInt(raw.substring(0, 4)) < 1000) {
                throw invalid();
            }
            LocalDateTime parsed = LocalDateTime.parse(raw, FORMATTER);
            String formatted = FORMATTER.format(parsed);
            if (!formatted.equals(raw)) {
                throw invalid();
            }
            return formatted;
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw invalid();
        }
    }

    public static boolean isCanonical(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (InvalidParameterException exception) {
            return false;
        }
    }

    private static InvalidParameterException invalid() {
        return new InvalidParameterException("时间格式无效");
    }
}
