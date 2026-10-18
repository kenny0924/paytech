package com.paytech.risk.decision.domain.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.databind.json.JsonMapper;

/** 运行期事实摘要：字符串值对象按 RFC 8785 规范化后取 SHA-256 小写十六进制。 */
public final class RuntimeHashes {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯函数工具。 */
    private RuntimeHashes() { }

    /**
     * 计算摘要
     *
     * @param schema 摘要合同标识，写入对象的 schema 字段
     * @param values 只含字符串或空值的字段
     * @return SHA-256 小写十六进制
     */
    public static String sha256(String schema, Map<String, String> values) {
        java.util.TreeMap<String, String> object = new java.util.TreeMap<>(values);
        object.put("schema", schema);
        byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(
            JSON.writeValueAsString(object).getBytes(StandardCharsets.UTF_8));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
