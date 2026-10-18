package com.paytech.gateway.test.simulator.bff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** 记录 BFF 收到的真实协议请求，便于验证 Gateway 的转发边界。 */
public final class BffRequestJournal {
    private final List<Entry> entries = new ArrayList<>();

    public synchronized void record(HttpMethod method, String path, HttpHeaders headers, byte[] body) {
        entries.add(new Entry(method, path, copyHeaders(headers), bodyDigest(body)));
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int count() {
        return entries.size();
    }

    public synchronized void reset() {
        entries.clear();
    }

    private Map<String, List<String>> copyHeaders(HttpHeaders headers) {
        Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
        headers.forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
        return Map.copyOf(copiedHeaders);
    }

    private String bodyDigest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", exception);
        }
    }

    /** BFF 收到的一次请求快照。 */
    public record Entry(HttpMethod method, String path, Map<String, List<String>> headers, String bodyDigest) {
        private static byte[] sha256(byte[] body) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(body);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 摘要算法不可用", exception);
            }
        }

        public String bodyDigestOf(String body) {
            return HexFormat.of().formatHex(sha256(body.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
