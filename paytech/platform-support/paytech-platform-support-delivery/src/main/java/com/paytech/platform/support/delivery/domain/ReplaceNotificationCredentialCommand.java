package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 以已存入 Vault 的新 Token 替换账号密钥。 */
@Getter
@AllArgsConstructor
public class ReplaceNotificationCredentialCommand {

    /** 账号编码。 */
    private final String providerCode;

    /** 新密钥的 Vault Token；不进入日志与审计。 */
    private final String credentialToken;

    /** 调用方看到的当前版本。 */
    private final int expectedVersion;

    /** 本次写入时间。 */
    private final Instant now;

    /** Token 不进入默认字符串表示。 */
    @Override
    public String toString() {
        return "ReplaceNotificationCredentialCommand[providerCode=" + providerCode + "]";
    }
}
