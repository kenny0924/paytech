package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 更换密钥或切换凭据方式；新密钥已在事务外存入 Vault，只携带新 Token。 */
@Getter
@AllArgsConstructor
public class ReplaceStorageCredentialCommand {

    /** 实例代码。 */
    private final String instanceCode;

    /** 目标凭据方式。 */
    private final FileStorageCredentialMode credentialMode;

    /** 新密钥 Vault Token；IAM_ROLE 为空。 */
    private final String credentialToken;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;

    /** 不输出 Token。 */
    @Override
    public String toString() {
        return "ReplaceStorageCredentialCommand[" + instanceCode + "]";
    }
}
