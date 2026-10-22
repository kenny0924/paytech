package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 存储实例凭据方式（t_file_storage_instance.credential_mode）。HTTP 字段使用 String。 */
public enum FileStorageCredentialMode implements CodeEnum {
    /** 静态访问密钥，原值只存 Vault，Platform 只保存 Token。 */
    STATIC("STATIC"),

    /** 使用运行环境默认凭据链（如 IAM 角色），不保存密钥。 */
    IAM_ROLE("IAM_ROLE");

    private final String code;

    FileStorageCredentialMode(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
