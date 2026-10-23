package com.paytech.platform.support.localization.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 小写十六进制摘要。 */
public final class Sha256 {

    private Sha256() {
    }

    /**
     * 字节摘要。
     *
     * @param bytes 内容
     * @return 64 位小写十六进制
     */
    public static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 不可用", unavailable);
        }
    }

    /**
     * UTF-8 文本摘要。
     *
     * @param text 文本
     * @return 64 位小写十六进制
     */
    public static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }
}
