package com.paytech.platform.support.service.processor.filestorage.request;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本更换密钥或切换凭据方式。 */
@Getter
@AllArgsConstructor
public class ReplaceStorageCredentialRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 实例代码。 */
    @NotBlank(message = "实例代码必填")
    private final String instanceCode;

    /** 凭据方式。 */
    @NotNull(message = "凭据方式必填")
    private final FileStorageCredentialMode credentialMode;

    /** 访问账号；STATIC 必填，IAM_ROLE 为空。不进入日志。 */
    private final String accessKey;

    /** 访问密钥；STATIC 必填，IAM_ROLE 为空。不进入日志。 */
    private final String secretKey;

    /** 调用方读取的原版本。 */
    @Positive(message = "原版本必须为正整数")
    private final int expectedVersion;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "ReplaceStorageCredentialRequest[REDACTED]";
    }
}
