package com.paytech.platform.support.delivery.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.paytech.foundation.common.error.ErrorException;

/**
 * Code-MoR-Signature 签名
 * <li>signedPayload = 秒级时间戳 + "." + 原始 Body；HMAC-SHA256，密钥为密钥字符串的 UTF-8 字节</li>
 * <li>Header 为 t=时间戳，后接每把密钥一个 v1，ACTIVE 在前、宽限密钥在后</li>
 */
public final class WebhookSignature {

    /** 签名 Header 名。 */
    public static final String HEADER = "Code-MoR-Signature";

    /** 密钥前缀。 */
    public static final String SECRET_PREFIX = "whsec_";

    /** 密钥值格式，与 Vault 数据类型的值格式一致。 */
    public static final String SECRET_PATTERN = "whsec_[A-Za-z0-9_-]{43}";

    /** 只承载静态方法。 */
    private WebhookSignature() {
    }

    /**
     * 计算签名 Header 值
     *
     * @param timestampSeconds 本次发送的 Unix 秒
     * @param body 发送的原始 Body
     * @param secrets 参与签名的密钥明文，至少一把
     * @return Header 值
     */
    public static String header(long timestampSeconds, byte[] body, List<String> secrets) {
        var header = new StringBuilder("t=").append(timestampSeconds);
        byte[] prefix = (timestampSeconds + ".").getBytes(StandardCharsets.US_ASCII);
        for (String secret : secrets) {
            header.append(",v1=").append(hmacHex(secret, prefix, body));
        }
        return header.toString();
    }

    /** 生成 32 字节随机密钥，编码为 whsec_ 加 43 位无填充 Base64URL。 */
    public static String generateSecret(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 对 prefix 与 body 的拼接计算 HMAC-SHA256 小写十六进制。 */
    static String hmacHex(String secret, byte[] prefix, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(prefix);
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException failure) {
            // 不保留原因链，避免密钥相关信息进入日志。
            throw new ErrorException("Webhook 签名计算失败");
        }
    }
}
