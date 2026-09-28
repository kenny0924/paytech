package com.paytech.vault.client;

import com.paytech.vault.client.internal.VaultValidation;

/**
 * 按明确用途查询；持有 Token 不代表调用方已完成业务授权。
 *
 * @param token
 *            43 字符的无填充 Base64URL 引用，只能进入请求体，不能进入 URL 或日志
 * @param purpose
 *            本次访问的明确用途，1..64 个字符的大写 Code
 */
public record RevealQuery(String token, String purpose) {
    public RevealQuery {
        VaultValidation.token(token);
        VaultValidation.code(purpose);
    }

    @Override
    public String toString() {
        return "RevealQuery[redacted]";
    }
}
