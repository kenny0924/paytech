package com.paytech.platform.support.accesscontrol.integration.signing;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import tools.jackson.databind.ObjectMapper;

/** 显式本地签名适配器；密钥只能外部注入，不能作为生产 KMS 的自动降级。 */
public class LocalHumanAuthenticationCryptography implements HumanAuthenticationCryptography {
    private final RSAPrivateCrtKey privateKey;
    private final byte[] hmacKey;
    private final int keyVersion;
    private final ObjectMapper json;
    private final String keyNamespace;

    // TODO by Codex: 生产接入真实 KMS Sign/HMAC，保留 ACTIVE Key 生命周期与公钥一致性检查。
    public LocalHumanAuthenticationCryptography(String encodedPrivateKey, String encodedHmacKey, int keyVersion,
        ObjectMapper json, String realm) {
        try {
            privateKey = (RSAPrivateCrtKey)KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encodedPrivateKey)));
            hmacKey = Base64.getDecoder().decode(encodedHmacKey);
        } catch (Exception exception) {
            throw new IllegalStateException("本地认证密钥未正确注入，禁止启动认证", exception);
        }
        if (privateKey.getModulus().bitLength() < 2048 || hmacKey.length < 32 || keyVersion < 1) {
            throw new IllegalStateException("本地认证密钥强度或版本无效");
        }
        if (!"OPERATIONS".equals(realm) && !"CUSTOMER".equals(realm)) {
            throw new IllegalArgumentException("本地签名 Realm 无效");
        }
        this.keyNamespace = "development:" + realm.toLowerCase(java.util.Locale.ROOT) + ":";
        this.keyVersion = keyVersion;
        this.json = json;
    }

    private static String unsigned(byte[] value) {
        return encode(value.length > 1 && value[0] == 0 ? java.util.Arrays.copyOfRange(value, 1, value.length) : value);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    @Override
    public String sign(String keyRef, String kid, String publicJwk, String payloadJson) {
        var jwk = json.readTree(publicJwk);
        String modulus = unsigned(privateKey.getModulus().toByteArray());
        String exponent = unsigned(privateKey.getPublicExponent().toByteArray());
        if (!(keyNamespace + kid).equals(keyRef) || !kid.equals(jwk.get("kid").asText())
            || !modulus.equals(jwk.get("n").asText()) || !exponent.equals(jwk.get("e").asText())) {
            throw new IllegalStateException("本地签名私钥与已初始化的 ACTIVE 公钥不匹配");
        }
        String header = json.writeValueAsString(java.util.Map.of("alg", "RS256", "typ", "JWT", "kid", kid));
        String input = encode(header.getBytes(StandardCharsets.UTF_8)) + "."
            + encode(payloadJson.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + encode(signature.sign());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("本地 JWT 签发失败", exception);
        }
    }

    @Override
    public VersionedHmacBatch versionedHmacs(List<HmacInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("HMAC 输入集合不能为空");
        }
        try {
            var digests = new java.util.ArrayList<String>(inputs.size());
            for (HmacInput input : inputs) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
                digests.add(HexFormat.of().formatHex(mac.doFinal(
                    (input.purpose() + "\\0" + input.value()).getBytes(StandardCharsets.UTF_8))));
            }
            return new VersionedHmacBatch(keyVersion, digests);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("认证 HMAC 不可用", exception);
        }
    }
}
