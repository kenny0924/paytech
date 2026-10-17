package com.paytech.vault.client;

import com.paytech.vault.client.internal.VaultValidation;

/**
 * 解密结果；仅用于当前已授权调用，不缓存、持久化或记录。
 *
 * @param value
 *            受保护的精确原值，允许空串，合法 UTF-8 编码不得超过 32 KiB
 */
public record RevealResult(String value) {
    public RevealResult {
        VaultValidation.value(value);
    }

    @Override
    public String toString() {
        return "RevealResult[redacted]";
    }
}
