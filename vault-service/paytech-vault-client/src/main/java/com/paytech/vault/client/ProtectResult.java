package com.paytech.vault.client;

import com.paytech.vault.client.internal.VaultValidation;

/**
 * 保护结果；Token 是不透明引用，不是业务授权凭据。
 *
 * @param token
 *            43 字符的无填充 Base64URL 引用，禁止输出到普通日志、事件或外部接口
 */
public record ProtectResult(String token) {
    public ProtectResult {
        VaultValidation.token(token);
    }

    @Override
    public String toString() {
        return "ProtectResult[redacted]";
    }
}
