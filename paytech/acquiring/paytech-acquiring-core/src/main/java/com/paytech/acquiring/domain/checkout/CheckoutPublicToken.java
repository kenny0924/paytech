package com.paytech.commerce.domain.checkout;

import com.paytech.foundation.token.PublicTokenGenerator;

/** Checkout 的大小写敏感公开 Token；通用主体校验由 Foundation 提供。 */
public record CheckoutPublicToken(String value) {
    public static final String PREFIX = "chk_";

    /** 仅接收带 Checkout 前缀且主体格式、校验码有效的值。 */
    public CheckoutPublicToken {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Checkout Public Token 格式或校验码无效");
        }
    }

    /** 解析完整 Checkout Token，不判断其存在性、状态或有效期。 */
    public static CheckoutPublicToken parse(String value) {
        return new CheckoutPublicToken(value);
    }

    /** 业务前缀由 Commerce 校验，移除前缀后复用公共主体校验。 */
    public static boolean isValid(String value) {
        return value != null && value.startsWith(PREFIX)
            && PublicTokenGenerator.isValid(value.substring(PREFIX.length()));
    }
}
