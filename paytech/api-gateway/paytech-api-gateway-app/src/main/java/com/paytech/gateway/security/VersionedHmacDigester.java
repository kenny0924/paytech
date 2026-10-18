package com.paytech.gateway.security;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/** 使用版本化 Access Log 密钥计算 Gateway 身份摘要。 */
@Component
public final class VersionedHmacDigester {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String ACCESS_IP_PURPOSE = "access-ip";
    private static final String ACCESS_SUBJECT_PURPOSE = "access-subject";

    private final Key accessLog;

    public VersionedHmacDigester(GatewayHmacProperties properties) {
        Objects.requireNonNull(properties, "Gateway HMAC 配置不可为空");
        accessLog = Key.from(properties.accessLog());
    }

    private static byte[] addressBytes(InetAddress address) {
        return Objects.requireNonNull(address, "摘要地址不可为空").getAddress();
    }

    private static byte[] utf8Bytes(String subject) {
        if (subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("摘要主体不可为空");
        }
        return subject.getBytes(StandardCharsets.UTF_8);
    }

    public VersionedDigest accessIpDigest(InetAddress address) {
        return accessDigest(ACCESS_IP_PURPOSE, addressBytes(address));
    }

    public VersionedDigest accessSubjectDigest(String subject) {
        return accessDigest(ACCESS_SUBJECT_PURPOSE, utf8Bytes(subject));
    }

    public VersionedDigest accessClientDigest(String clientId) {
        return accessDigest("access-client", utf8Bytes(clientId));
    }

    public VersionedDigest accessCredentialDigest(String credentialId) {
        return accessDigest("access-credential", utf8Bytes(credentialId));
    }

    private VersionedDigest accessDigest(String purpose, byte[] value) {
        try {
            return digest(accessLog, purpose, value);
        } finally {
            Arrays.fill(value, (byte)0);
        }
    }

    private VersionedDigest digest(Key key, String purpose, byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key.material, HMAC_SHA_256));
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte)0);
            byte[] output = mac.doFinal(value);
            try {
                return new VersionedDigest(key.version, Base64.getUrlEncoder().withoutPadding().encodeToString(output));
            } finally {
                Arrays.fill(output, (byte)0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 初始化失败", exception);
        }
    }

    /** 版本标识和不可逆摘要，不携带原始身份输入。 */
    public record VersionedDigest(String version, String digest) {
        public VersionedDigest {
            if (version == null || version.isBlank() || digest == null || digest.isBlank()) {
                throw new IllegalArgumentException("HMAC 摘要结果不可为空");
            }
        }
    }

    private static final class Key {
        private final String version;
        private final byte[] material;

        private Key(String version, byte[] material) {
            this.version = version;
            this.material = material;
        }

        private static Key from(GatewayHmacProperties.Secret secret) {
            return new Key(secret.version(), secret.materialCopy());
        }
    }
}
