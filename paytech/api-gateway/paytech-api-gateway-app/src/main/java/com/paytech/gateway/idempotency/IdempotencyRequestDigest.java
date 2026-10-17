package com.paytech.gateway.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 请求摘要 v1：SHA-256(UTF-8("v1\n" + 方法 + "\n" + 路由编码 + "\n" + 原始路径 + "\n" + 规范化 Query + "\n")
 * ‖ SHA-256(请求体原始字节))。不做 JSON 规范化，客户端重试必须发送相同字节。
 */
@Component
public class IdempotencyRequestDigest {
    private static final String VERSION = "v1";

    public String digest(String method, String routeCode, String rawPath, String rawQuery, byte[] body) {
        String head = VERSION + "\n" + method + "\n" + routeCode + "\n" + rawPath + "\n" + normalizeQuery(rawQuery)
            + "\n";
        MessageDigest digest = sha256();
        digest.update(head.getBytes(StandardCharsets.UTF_8));
        digest.update(sha256().digest(body));
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Key 原值不进入 Redis Key，只使用其固定长度摘要。 */
    public String keyHash(String idempotencyKey) {
        return HexFormat.of().formatHex(sha256().digest(idempotencyKey.getBytes(StandardCharsets.UTF_8)));
    }

    /** 按参数名稳定排序：同名参数保持原顺序，名称与值都保留原始编码。 */
    static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String> parameters = new ArrayList<>(Arrays.asList(rawQuery.split("&", -1)));
        parameters.sort(Comparator.comparing(IdempotencyRequestDigest::parameterName));
        return String.join("&", parameters);
    }

    private static String parameterName(String parameter) {
        int separator = parameter.indexOf('=');
        return separator < 0 ? parameter : parameter.substring(0, separator);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
