package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建 DRAFT 存储实例；STATIC 的密钥已在事务外存入 Vault，只携带 Token。 */
@Getter
@AllArgsConstructor
public class CreateStorageInstanceCommand {

    /** 预先生成的实例主键。 */
    private final long instanceId;

    /** 实例代码。 */
    private final String instanceCode;

    /** 连接设置。 */
    private final StorageInstanceSettings settings;

    /** 凭据方式。 */
    private final FileStorageCredentialMode credentialMode;

    /** 密钥 Vault Token；IAM_ROLE 为空。 */
    private final String credentialToken;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;

    /** 不输出 Token。 */
    @Override
    public String toString() {
        return "CreateStorageInstanceCommand[" + instanceCode + "]";
    }
}
