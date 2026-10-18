package com.paytech.vault.client;

import com.paytech.vault.client.internal.VaultValidation;

/**
 * 原值与逻辑命令键只保留在调用边界，不参与日志输出；本对象不执行业务规范化。
 *
 * @param dataType
 *            已注册的数据类型，1..64 个字符的大写 Code
 * @param tokenMode
 *            数据类型已配置的 Token 模式，不得由同类请求任意切换
 * @param value
 *            精确原值，允许空串，合法 UTF-8 编码不得超过 32 KiB
 * @param purpose
 *            本次操作的明确用途，1..64 个字符的大写 Code
 * @param idempotencyKey
 *            调用方为逻辑命令生成的 1..128 字符安全 ASCII 键；重放时复用
 */
public record ProtectCommand(String dataType, TokenMode tokenMode, String value, String purpose, String idempotencyKey) {
    public ProtectCommand {
        VaultValidation.code(dataType);
        VaultValidation.required(tokenMode);
        VaultValidation.value(value);
        VaultValidation.code(purpose);
        VaultValidation.safeHeader(idempotencyKey, 128);
    }

    @Override
    public String toString() {
        return "ProtectCommand[redacted]";
    }
}
