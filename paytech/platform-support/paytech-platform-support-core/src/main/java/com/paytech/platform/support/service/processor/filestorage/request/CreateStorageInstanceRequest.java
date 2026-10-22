package com.paytech.platform.support.service.processor.filestorage.request;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.filedocument.domain.storage.StorageInstanceSettings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建 DRAFT 存储实例。 */
@Getter
@AllArgsConstructor
public class CreateStorageInstanceRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 实例代码。 */
    @NotBlank(message = "实例代码必填")
    private final String instanceCode;

    /** 连接设置；修改时 storageType 为空，沿用原类型。 */
    @NotNull(message = "连接设置必填")
    private final StorageInstanceSettings settings;

    /** 凭据方式。 */
    @NotNull(message = "凭据方式必填")
    private final FileStorageCredentialMode credentialMode;

    /** 访问账号；STATIC 必填，IAM_ROLE 为空。不进入日志。 */
    private final String accessKey;

    /** 访问密钥；STATIC 必填，IAM_ROLE 为空。不进入日志。 */
    private final String secretKey;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "CreateStorageInstanceRequest[REDACTED]";
    }
}
