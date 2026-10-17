package com.paytech.acquiring.domain.checkout;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/** Checkout Session 创建时冻结的服务端公开 HTTPS Origin。 */
public record CheckoutPublicOrigin(String value) {

    /** 解析并验证不携带凭据、路径、查询或片段的 ASCII HTTPS Origin。 */
    public static CheckoutPublicOrigin parse(String value) {
        return new CheckoutPublicOrigin(value);
    }

    public CheckoutPublicOrigin {
        if (value == null || value.isBlank() || value.length() > 255
            || !StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
            throw new IllegalArgumentException("Checkout Public Origin 必须是不超过 255 字符的非空 ASCII HTTPS Origin");
        }
        URI origin;
        try {
            origin = new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Checkout Public Origin 格式不正确", exception);
        }
        boolean pathless = origin.getRawPath() == null || origin.getRawPath().isEmpty();
        if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getHost().isBlank()
            || origin.getRawUserInfo() != null || !pathless || origin.getRawQuery() != null
            || origin.getRawFragment() != null || value.endsWith("/") || !value.equals(origin.toASCIIString())) {
            throw new IllegalArgumentException("Checkout Public Origin 必须是不带凭据、路径、查询、片段或尾斜线的 HTTPS Origin");
        }
    }

    /** 拼接大小写敏感的 Hosted Checkout Public Token。 */
    public String checkoutUrl(String publicToken) {
        if (publicToken == null || publicToken.isBlank()) {
            throw new IllegalArgumentException("Checkout Public Token 不能为空");
        }
        return value + "/c/" + publicToken;
    }
}
