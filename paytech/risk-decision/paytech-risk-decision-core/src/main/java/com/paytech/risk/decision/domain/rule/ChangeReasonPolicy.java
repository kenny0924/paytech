package com.paytech.risk.decision.domain.rule;

import com.paytech.foundation.common.error.InvalidParameterException;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow storage policy for operator-supplied rule-set change reasons. */
public final class ChangeReasonPolicy {

    private static final int MAX_LENGTH = 500;
    private static final Pattern CREDENTIAL_ASSIGNMENT = Pattern.compile(
        "(?iu)(?:^|[^\\p{L}\\p{N}_])"
            + "(?:api[ _-]?key|client[ _-]?secret|secret|password|passwd|pwd|"
            + "access[ _-]?token|refresh[ _-]?token|auth[ _-]?token|token)"
            + "\\s*[:=]\\s*(?:\\\"[^\\\"\\r\\n]+\\\"|'[^'\\r\\n]+'|[^\\s,;]+)");
    private static final Pattern AUTHORIZATION_BEARER = Pattern.compile(
        "(?iu)(?:^|[^\\p{L}\\p{N}_])authorization\\s*[:=]\\s*bearer\\s+"
            + "[a-z0-9._~+/=-]+(?=$|[\\s,;])");
    private static final Pattern BEARER_VALUE = Pattern.compile(
        "(?iu)(?:^|[^\\p{L}\\p{N}_])bearer\\s+([a-z0-9._~+/=-]{8,})(?=$|[\\s,;])");
    private static final Pattern EMAIL = Pattern.compile(
        "(?i)(?<![a-z0-9._%+-])[a-z0-9][a-z0-9._%+-]{0,63}@"
            + "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(?![a-z0-9-])");
    private static final Pattern PHONE_ASSIGNMENT = Pattern.compile(
        "(?iu)(?:^|[^\\p{L}\\p{N}_])(?:phone|mobile|telephone|tel)(?:[ _-]?(?:number|no))?"
            + "\\s*[:=]\\s*(\\+?[0-9(][0-9() .\\u00a0\\u202f\\p{Pd}]{5,30})(?=$|[^\\p{L}\\p{N}])");
    private static final Pattern CHINESE_PHONE_ASSIGNMENT = Pattern.compile(
        "(?:电话|手机(?:号)?)\\s*[:=：]\\s*(\\+?[0-9(][0-9() .\\u00a0\\u202f\\p{Pd}]{5,30})"
            + "(?=$|[^\\p{L}\\p{N}])");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile(
        "(?<![\\p{L}\\p{N}])\\+[1-9][0-9() .\\u00a0\\u202f\\p{Pd}]{5,30}(?![\\p{L}\\p{N}])");
    private static final Pattern GROUPED_PHONE = Pattern.compile(
        "(?<![0-9])[0-9]{3}[ \\u00a0\\u202f\\p{Pd}][0-9]{3}"
            + "[ \\u00a0\\u202f\\p{Pd}][0-9]{4}(?![0-9])");
    private static final Pattern PARENTHESIZED_PHONE = Pattern.compile(
        "(?<![0-9])\\([0-9]{2,4}\\)[ \\u00a0\\u202f\\p{Pd}]?[0-9]{3}"
            + "[ \\u00a0\\u202f\\p{Pd}][0-9]{4}(?![0-9])");

    private ChangeReasonPolicy() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH
            || containsNonDisplayCharacter(normalized)
            || JsonContainer.isComplete(normalized)
            || CREDENTIAL_ASSIGNMENT.matcher(normalized).find()
            || containsBearerCredential(normalized)
            || EMAIL.matcher(normalized).find()
            || containsPhoneNumber(normalized)
            || containsLuhnPanCandidate(normalized)) {
            throw invalid();
        }
        return normalized;
    }

    private static boolean containsNonDisplayCharacter(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE
                || type == Character.PRIVATE_USE || type == Character.UNASSIGNED
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                || codePoint >= 0xFDD0 && codePoint <= 0xFDEF
                || (codePoint & 0xFFFF) == 0xFFFE || (codePoint & 0xFFFF) == 0xFFFF) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsBearerCredential(String value) {
        return AUTHORIZATION_BEARER.matcher(value).find() || BEARER_VALUE.matcher(value).find();
    }

    private static boolean containsPhoneNumber(String value) {
        return containsPhoneCandidate(PHONE_ASSIGNMENT.matcher(value), 1)
            || containsPhoneCandidate(CHINESE_PHONE_ASSIGNMENT.matcher(value), 1)
            || containsPhoneCandidate(INTERNATIONAL_PHONE.matcher(value), 0)
            || containsPhoneCandidate(GROUPED_PHONE.matcher(value), 0)
            || containsPhoneCandidate(PARENTHESIZED_PHONE.matcher(value), 0);
    }

    private static boolean containsPhoneCandidate(Matcher matcher, int group) {
        while (matcher.find()) {
            int digits = 0;
            String candidate = matcher.group(group);
            for (int index = 0; index < candidate.length(); index++) {
                if (isAsciiDigit(candidate.charAt(index))) {
                    digits++;
                }
            }
            if (digits >= 7 && digits <= 15) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLuhnPanCandidate(String value) {
        int index = 0;
        while (index < value.length()) {
            if (!isAsciiDigit(value.charAt(index))) {
                index++;
                continue;
            }
            StringBuilder digits = new StringBuilder(19);
            int cursor = index;
            while (cursor < value.length()) {
                char current = value.charAt(cursor);
                if (isAsciiDigit(current)) {
                    digits.append(current);
                } else if (!isPanSeparator(current)) {
                    break;
                }
                cursor++;
            }
            if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
                return true;
            }
            index = Math.max(cursor, index + 1);
        }
        return false;
    }

    private static boolean passesLuhn(CharSequence digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isPanSeparator(char value) {
        int type = Character.getType(value);
        return type == Character.SPACE_SEPARATOR || type == Character.DASH_PUNCTUATION || value == '\u2212';
    }

    private static InvalidParameterException invalid() {
        return new InvalidParameterException("变更原因包含不允许的内容");
    }

    private static final class JsonContainer {

        private final String source;
        private int index;

        private JsonContainer(String source) {
            this.source = source;
        }

        static boolean isComplete(String source) {
            if (source.isEmpty() || source.charAt(0) != '{' && source.charAt(0) != '[') {
                return false;
            }
            JsonContainer parser = new JsonContainer(source);
            return parser.value() && parser.skipWhitespace() && parser.index == source.length();
        }

        private boolean value() {
            if (index >= source.length()) {
                return false;
            }
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> keyword("true");
                case 'f' -> keyword("false");
                case 'n' -> keyword("null");
                default -> number();
            };
        }

        private boolean object() {
            index++;
            skipWhitespace();
            if (consume('}')) {
                return true;
            }
            do {
                if (!string() || !skipWhitespace() || !consume(':') || !skipWhitespace() || !value()) {
                    return false;
                }
                skipWhitespace();
                if (consume('}')) {
                    return true;
                }
            } while (consume(',') && skipWhitespace());
            return false;
        }

        private boolean array() {
            index++;
            skipWhitespace();
            if (consume(']')) {
                return true;
            }
            do {
                if (!value()) {
                    return false;
                }
                skipWhitespace();
                if (consume(']')) {
                    return true;
                }
            } while (consume(',') && skipWhitespace());
            return false;
        }

        private boolean string() {
            if (!consume('"')) {
                return false;
            }
            while (index < source.length()) {
                char current = source.charAt(index++);
                if (current == '"') {
                    return true;
                }
                if (current < 0x20) {
                    return false;
                }
                if (current == '\\') {
                    if (index >= source.length()) {
                        return false;
                    }
                    char escaped = source.charAt(index++);
                    if ("\"\\/bfnrt".indexOf(escaped) >= 0) {
                        continue;
                    }
                    if (escaped != 'u' || index + 4 > source.length()) {
                        return false;
                    }
                    for (int count = 0; count < 4; count++) {
                        if (Character.digit(source.charAt(index++), 16) < 0) {
                            return false;
                        }
                    }
                }
            }
            return false;
        }

        private boolean number() {
            int start = index;
            consume('-');
            if (consume('0')) {
                if (index < source.length() && isAsciiDigit(source.charAt(index))) {
                    return false;
                }
            } else if (index < source.length() && source.charAt(index) >= '1' && source.charAt(index) <= '9') {
                while (index < source.length() && isAsciiDigit(source.charAt(index))) {
                    index++;
                }
            } else {
                return false;
            }
            if (consume('.')) {
                int fraction = index;
                while (index < source.length() && isAsciiDigit(source.charAt(index))) {
                    index++;
                }
                if (fraction == index) {
                    return false;
                }
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }
                int exponent = index;
                while (index < source.length() && isAsciiDigit(source.charAt(index))) {
                    index++;
                }
                if (exponent == index) {
                    return false;
                }
            }
            return index > start;
        }

        private boolean keyword(String value) {
            if (!source.startsWith(value, index)) {
                return false;
            }
            index += value.length();
            return true;
        }

        private boolean skipWhitespace() {
            while (index < source.length()) {
                char current = source.charAt(index);
                if (current != ' ' && current != '\t' && current != '\r' && current != '\n') {
                    break;
                }
                index++;
            }
            return true;
        }

        private boolean consume(char expected) {
            if (index >= source.length() || source.charAt(index) != expected) {
                return false;
            }
            index++;
            return true;
        }
    }
}
