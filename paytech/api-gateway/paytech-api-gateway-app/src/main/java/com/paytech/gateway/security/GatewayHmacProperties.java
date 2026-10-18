package com.paytech.gateway.security;

import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gateway Access Log HMAC 的已解码 Secret 配置。 */
@ConfigurationProperties("gateway.operations.hmac")
public record GatewayHmacProperties(Secret accessLog) {
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public GatewayHmacProperties {
        if (accessLog == null) {
            throw new IllegalArgumentException("访问日志 HMAC 配置不可为空");
        }
    }

    /** 仅保存私有已解码字节的 HMAC Secret。 */
    public static final class Secret {
        private final String version;
        private final byte[] material;

        public Secret(String version, String encodedMaterial) {
            if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
                throw new IllegalArgumentException("HMAC 密钥版本标识格式无效");
            }
            material = decode(encodedMaterial);
            this.version = version;
        }

        private static byte[] decode(String encodedMaterial) {
            if (encodedMaterial == null || encodedMaterial.isBlank()) {
                throw new IllegalArgumentException("HMAC 密钥不可为空");
            }
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedMaterial);
                if (decoded.length < 32) {
                    Arrays.fill(decoded, (byte)0);
                    throw new IllegalArgumentException("HMAC 密钥解码后长度不得小于 32 字节");
                }
                return decoded;
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("HMAC 密钥必须是有效 Base64 且解码后至少 32 字节");
            }
        }

        public String version() {
            return version;
        }

        byte[] materialCopy() {
            return Arrays.copyOf(material, material.length);
        }

        @Override
        public String toString() {
            return "Secret[version=" + version + "]";
        }
    }
}
