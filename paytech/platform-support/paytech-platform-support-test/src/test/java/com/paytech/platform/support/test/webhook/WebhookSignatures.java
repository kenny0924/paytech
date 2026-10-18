package com.paytech.platform.support.test.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 按 Client 的验签方式独立解析与校验 Code-MoR-Signature，不调用生产签名代码。 */
public final class WebhookSignatures {

    /** 只承载静态方法。 */
    private WebhookSignatures() {
    }

    /** 解析出的 Header。 */
    public record Parsed(long timestamp, List<String> signatures) {
    }

    /** 解析 t= 与一个或多个 v1=。 */
    public static Parsed parse(String header) {
        long timestamp = -1;
        var signatures = new ArrayList<String>();
        for (String part : header.split(",")) {
            if (part.startsWith("t=")) {
                timestamp = Long.parseLong(part.substring(2));
            } else if (part.startsWith("v1=")) {
                signatures.add(part.substring(3));
            }
        }
        return new Parsed(timestamp, List.copyOf(signatures));
    }

    /** 期望的 v1：HMAC-SHA256(密钥 UTF-8 字节, 时间戳 + "." + 原始 Body)。 */
    public static String expected(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
