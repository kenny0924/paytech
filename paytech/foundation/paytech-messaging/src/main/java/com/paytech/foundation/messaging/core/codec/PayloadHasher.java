package com.paytech.foundation.messaging.core.codec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 信封 {@code payloadHash}：Payload 的 RFC 8785 规范 UTF-8 bytes 的 SHA-256，格式 {@code sha256:<64 位小写十六进制>}。
 * <li>只在 {@code paytech-messaging}（发布端与消费端解码）计算，{@code -core} 与 {@code paytech-messaging-api} 不依赖规范化 JAR。</li>
 *
 * @author Claude
 */
public final class PayloadHasher {

    private static final String PREFIX = "sha256:";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private PayloadHasher() {
    }

    /**
     * 计算 Payload 哈希。
     *
     * @param payload JSON Object
     * @return {@code sha256:<hex>}
     * @throws IllegalArgumentException Payload 超出 Foundation JSON Object Profile（例如含小数或负数）
     */
    public static String hash(ObjectNode payload) {
        try {
            byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(MAPPER.writeValueAsBytes(payload));
            return PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonCanonicalizationException exception) {
            throw new IllegalArgumentException("payload is outside the JSON Object Profile", exception);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
