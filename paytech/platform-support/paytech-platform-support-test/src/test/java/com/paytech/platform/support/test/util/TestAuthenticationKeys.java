package com.paytech.platform.support.test.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 测试侧密钥材料：从 application-mock.yml 的合成私钥推导公钥 JWK，并按相同协议计算 HMAC 与 Argon2id PHC。
 *
 * <p>只服务于直接插入的前置数据；生产签发、验签与校验仍由被测链路执行。
 */
@TestComponent
@RequiredArgsConstructor
public class TestAuthenticationKeys {

    private static final Argon2PasswordEncoder PASSWORDS = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);

    private final Environment environment;
    private final ObjectMapper json;

    /** Realm 当前私钥对应的公开 JWK，字段顺序固定。 */
    public String publicJwk(String realm, String kid) {
        RSAPrivateCrtKey key = privateKey(realm);
        var jwk = new LinkedHashMap<String, Object>();
        jwk.put("alg", "RS256");
        jwk.put("e", unsigned(key.getPublicExponent().toByteArray()));
        jwk.put("key_ops", List.of("verify"));
        jwk.put("kid", kid);
        jwk.put("kty", "RSA");
        jwk.put("n", unsigned(key.getModulus().toByteArray()));
        jwk.put("use", "sig");
        return json.writeValueAsString(jwk);
    }

    /** 本地签名适配器要求的 Key Ref 命名空间。 */
    public String keyRef(String realm, String kid) {
        return "development:" + realm.toLowerCase(Locale.ROOT) + ":" + kid;
    }

    /** 与认证 HMAC 端口相同的输入格式和 Key。 */
    public String hmac(String realm, String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(
                environment.getRequiredProperty(prefix(realm) + "_LOCAL_HMAC_KEY_BASE64")), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + "\\0" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("测试 HMAC 计算失败", failure);
        }
    }

    /** 用 Realm 公钥验证 RS256 签名并返回 Header 与 Claims；签名无效时测试失败。 */
    public tools.jackson.databind.JsonNode[] verifiedJwt(String realm, String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new AssertionError("JWT 必须由三段组成");
        }
        try {
            RSAPrivateCrtKey key = privateKey(realm);
            var publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new java.security.spec.RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
            var signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new AssertionError("JWT 签名无效");
            }
        } catch (java.security.GeneralSecurityException failure) {
            throw new AssertionError("JWT 验签失败", failure);
        }
        return new tools.jackson.databind.JsonNode[] {json.readTree(Base64.getUrlDecoder().decode(parts[0])),
            json.readTree(Base64.getUrlDecoder().decode(parts[1]))};
    }

    /** 与生产参数一致的 Argon2id PHC。 */
    public String passwordPhc(String password) {
        return PASSWORDS.encode(password);
    }

    private RSAPrivateCrtKey privateKey(String realm) {
        try {
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(environment.getRequiredProperty(prefix(realm) + "_LOCAL_SIGNING_PRIVATE_KEY_BASE64"))));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("测试签名私钥无效", failure);
        }
    }

    private static String prefix(String realm) {
        return "OPERATIONS".equals(realm) ? "OPERATIONS" : "CLIENT";
    }

    private static String unsigned(byte[] value) {
        byte[] bytes = value.length > 1 && value[0] == 0 ? Arrays.copyOfRange(value, 1, value.length) : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
