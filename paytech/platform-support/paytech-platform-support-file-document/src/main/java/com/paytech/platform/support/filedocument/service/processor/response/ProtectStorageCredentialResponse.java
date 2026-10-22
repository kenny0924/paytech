package com.paytech.platform.support.filedocument.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Vault 返回的新密钥 Token；未被实例引用前不生效。 */
@Getter
@AllArgsConstructor
public class ProtectStorageCredentialResponse {

    /** 新密钥 Vault Token，不进入日志与响应。 */
    private final String credentialToken;

    /** 不输出 Token。 */
    @Override
    public String toString() {
        return "ProtectStorageCredentialResponse[REDACTED]";
    }
}
