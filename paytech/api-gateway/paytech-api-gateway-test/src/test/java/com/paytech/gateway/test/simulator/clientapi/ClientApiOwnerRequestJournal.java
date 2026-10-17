package com.paytech.gateway.test.simulator.clientapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** 仅记录声明过的协议路径、受控 Header 和 Body 长度，不保留认证值或 Body。 */
public final class ClientApiOwnerRequestJournal {
    private static final Set<String> OBSERVABLE_HEADERS = Set.of("content-type", "idempotency-key", "x-request-id");
    // 保留禁止转发的固定 Header 名用于边界断言，但绝不保存这些 Header 的值。
    private static final Set<String> OBSERVABLE_NAMES = Set.of("authorization", "cookie", "x-entry-channel",
        "x-real-ip", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
        "x-gateway-tenant-id", "x-gateway-scope", "x-gateway-route-code", "x-gateway-channel",
        "x-gateway-client-id", "x-gateway-credential-id", "x-platform-api-credential", "x-unapproved");
    private final List<Entry> entries = new ArrayList<>();
    private String expectedClientDigest;
    private String expectedCredentialDigest;

    public synchronized void expectIdentity(String client, String credential) {
        expectedClientDigest = digest(client);
        expectedCredentialDigest = digest(credential);
    }

    synchronized void record(HttpMethod method, String rawPath, HttpHeaders headers, int bodyLength) {
        // 保留 Header 原值只存在于本次调用栈，用于阻止 Path 或安全 Header 反射身份值。
        List<String> sensitiveValues = new ArrayList<>();
        headers.forEach((name, values) -> {
            if (reservedHeader(name.toLowerCase(Locale.ROOT))) {
                for (String value : values) {
                    if (!value.isEmpty()) {
                        sensitiveValues.add(value);
                        if (name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)
                            && value.regionMatches(true, 0, "Bearer ", 0, 7) && value.length() > 7) {
                            sensitiveValues.add(value.substring(7));
                        }
                    }
                }
            }
        });
        Map<String, List<String>> safeHeaders = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        headers.forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (OBSERVABLE_HEADERS.contains(normalized) || OBSERVABLE_NAMES.contains(normalized)) {
                names.add(normalized);
            } else if (normalized.startsWith("x-gateway-")) {
                names.add("reserved-gateway-header");
            } else if (normalized.startsWith("x-forwarded-")) {
                names.add("reserved-forwarded-header");
            }
            if (OBSERVABLE_HEADERS.contains(normalized)) {
                safeHeaders.put(normalized, values.stream().map(value -> redact(value, sensitiveValues)).toList());
            }
        });
        boolean identityMatches = expectedClientDigest != null && expectedCredentialDigest != null
            && headers.getOrEmpty("X-Gateway-Client-Id").size() == 1
            && headers.getOrEmpty("X-Gateway-Credential-Id").size() == 1
            && expectedClientDigest.equals(digest(headers.getFirst("X-Gateway-Client-Id")))
            && expectedCredentialDigest.equals(digest(headers.getFirst("X-Gateway-Credential-Id")));
        entries.add(new Entry(method, redact(rawPath, sensitiveValues), Set.copyOf(names), Map.copyOf(safeHeaders),
            bodyLength, identityMatches));
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int count() {
        return entries.size();
    }

    public synchronized void reset() {
        entries.clear();
        expectedClientDigest = null;
        expectedCredentialDigest = null;
    }

    private String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("测试摘要算法不可用", exception);
        }
    }

    private String redact(String value, List<String> credentials) {
        return credentials.stream().anyMatch(value::contains) ? "[REDACTED]" : value;
    }

    private boolean reservedHeader(String normalized) {
        return OBSERVABLE_NAMES.contains(normalized) || normalized.startsWith("x-gateway-")
            || normalized.startsWith("x-forwarded-");
    }

    public record Entry(HttpMethod method, String rawPath, Set<String> headerNames,
        Map<String, List<String>> headers, int bodyLength, boolean identityMatches) {}
}
