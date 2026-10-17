package com.paytech.gateway.routing.runtime;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** 已校验的固定 Segment 与单 Segment Variable Path Template。 */
public final class GatewayPathTemplate {
    private static final int MAX_PATH_LENGTH = 1_000;
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern FIXED_SEGMENT = Pattern.compile("(?:[A-Za-z0-9._~!$&'+,;=:@-]|%[0-9A-F]{2})+");
    private static final Pattern VARIABLE_VALUE_SEGMENT =
        Pattern.compile("(?:[A-Za-z0-9._~!$&'()*+,;=:@-]|%[0-9A-F]{2})+");
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final String value;
    private final List<Segment> segments;
    private final Set<String> variableNames;

    private GatewayPathTemplate(String value, List<Segment> segments, Set<String> variableNames) {
        this.value = value;
        this.segments = List.copyOf(segments);
        this.variableNames = Set.copyOf(variableNames);
    }

    public static GatewayPathTemplate compile(String value) {
        requirePathEnvelope(value);
        List<String> rawSegments = split(value);
        List<Segment> segments = new ArrayList<>(rawSegments.size());
        Set<String> variables = new HashSet<>();
        for (String rawSegment : rawSegments) {
            if (rawSegment.startsWith("{") && rawSegment.endsWith("}")) {
                String variable = rawSegment.substring(1, rawSegment.length() - 1);
                if (!VARIABLE_NAME.matcher(variable).matches() || !variables.add(variable)) {
                    throw new IllegalArgumentException("Gateway Path Variable 非法或重复");
                }
                segments.add(new Segment(true, variable));
                continue;
            }
            requireFixedSegment(rawSegment);
            segments.add(new Segment(false, rawSegment));
        }
        return new GatewayPathTemplate(value, segments, variables);
    }

    private static void requirePathEnvelope(String path) {
        if (path == null || path.isBlank() || path.length() > MAX_PATH_LENGTH
            || !StandardCharsets.US_ASCII.newEncoder().canEncode(path) || !path.startsWith("/") || path.contains("//")
            || path.indexOf('\\') >= 0 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Gateway Path 非法");
        }
    }

    private static List<String> split(String path) {
        if ("/".equals(path)) {
            return List.of();
        }
        String[] values = path.substring(1).split("/", -1);
        List<String> result = List.of(values);
        if (result.stream().anyMatch(value -> value.isEmpty() || ".".equals(value) || "..".equals(value))) {
            throw new IllegalArgumentException("Gateway Path 包含空或 Dot Segment");
        }
        return result;
    }

    private static void requireFixedSegment(String segment) {
        if (segment.indexOf('{') >= 0 || segment.indexOf('}') >= 0 || containsPatternSyntax(segment)
            || !FIXED_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("Gateway Path Segment 非法");
        }
        validatePercentEncoding(segment);
    }

    private static void requireVariableValueSegment(String segment) {
        if (!VARIABLE_VALUE_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("Gateway Path Variable Value 非法");
        }
        validatePercentEncoding(segment);
    }

    private static boolean containsPatternSyntax(String segment) {
        return segment.indexOf('*') >= 0 || segment.indexOf('[') >= 0 || segment.indexOf(']') >= 0
            || segment.indexOf('(') >= 0 || segment.indexOf(')') >= 0 || segment.indexOf('|') >= 0
            || segment.indexOf('^') >= 0 || segment.indexOf('$') >= 0;
    }

    private static void validatePercentEncoding(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '%') {
                continue;
            }
            if (index + 2 >= value.length() || !isUpperHex(value.charAt(index + 1))
                || !isUpperHex(value.charAt(index + 2))) {
                throw new IllegalArgumentException("Gateway Path Percent Encoding 非法");
            }
            int decoded = hex(value.charAt(index + 1)) * 16 + hex(value.charAt(index + 2));
            if (decoded == '/' || decoded == '\\' || unreserved(decoded)) {
                throw new IllegalArgumentException("Gateway Path Percent Encoding 非规范或危险");
            }
            index += 2;
        }
    }

    private static String decode(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '%') {
                bytes.write(current);
                continue;
            }
            bytes.write(hex(value.charAt(index + 1)) * 16 + hex(value.charAt(index + 2)));
            index += 2;
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray()));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Gateway Path Variable 不是有效 UTF-8", exception);
        }
    }

    private static String encode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Gateway Path Variable 缺失");
        }
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(value);
        StringBuilder encoded = new StringBuilder(bytes.remaining() * 3);
        while (bytes.hasRemaining()) {
            int current = Byte.toUnsignedInt(bytes.get());
            if (unreserved(current)) {
                encoded.append((char)current);
                continue;
            }
            encoded.append('%').append(HEX[current >>> 4]).append(HEX[current & 0x0F]);
        }
        return encoded.toString();
    }

    private static boolean isUpperHex(char value) {
        return value >= '0' && value <= '9' || value >= 'A' && value <= 'F';
    }

    private static int hex(char value) {
        return value <= '9' ? value - '0' : value - 'A' + 10;
    }

    private static boolean unreserved(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value >= '0' && value <= '9'
            || value == '-' || value == '.' || value == '_' || value == '~';
    }

    public String value() {
        return value;
    }

    public Set<String> variableNames() {
        return variableNames;
    }

    public int variableCount() {
        return variableNames.size();
    }

    public List<PathSegment> shape() {
        return segments.stream().map(segment -> new PathSegment(segment.variable(), segment.value())).toList();
    }

    public Optional<Map<String, String>> match(String rawPath) {
        List<String> candidates;
        try {
            requirePathEnvelope(rawPath);
            candidates = split(rawPath);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (candidates.size() != segments.size()) {
            return Optional.empty();
        }
        Map<String, String> variables = new HashMap<>();
        for (int index = 0; index < segments.size(); index++) {
            Segment template = segments.get(index);
            String candidate = candidates.get(index);
            if (!template.variable()) {
                if (!template.value().equals(candidate)) {
                    return Optional.empty();
                }
                continue;
            }
            String decoded;
            try {
                requireVariableValueSegment(candidate);
                decoded = decode(candidate);
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
            if (".".equals(decoded) || "..".equals(decoded) || decoded.indexOf('/') >= 0 || decoded.indexOf('\\') >= 0
                || decoded.codePoints().anyMatch(Character::isISOControl)) {
                return Optional.empty();
            }
            variables.put(template.value(), decoded);
        }
        return Optional.of(Map.copyOf(variables));
    }

    public String render(Map<String, String> variables) {
        StringBuilder path = new StringBuilder();
        for (Segment segment : segments) {
            path.append('/');
            path.append(segment.variable() ? encode(variables.get(segment.value())) : segment.value());
        }
        return path.isEmpty() ? "/" : path.toString();
    }

    public record PathSegment(boolean variable, String value) {
    }

    private record Segment(boolean variable, String value) {
    }
}
