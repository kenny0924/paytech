package com.paytech.platform.support.delivery.service.processor.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Vault 返回的密钥 Token；只交给账号写入，不进入日志、审计或接口响应。 */
@Getter
@RequiredArgsConstructor
public class ProtectNotificationCredentialResponse {

    /** 密钥对的 Vault Token。 */
    private final String credentialToken;

    /** 不输出 Token。 */
    @Override
    public String toString() {
        return "ProtectNotificationCredentialResponse[redacted]";
    }
}
