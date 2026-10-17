package com.paytech.platform.support.delivery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 把发送账号的 IAM 密钥对存入 Vault
 * <li>toString 只输出账号身份，不输出密钥</li>
 */
@Getter
@AllArgsConstructor
public class ProtectNotificationCredentialRequest {

    /** 账号身份，用于 Vault 幂等键。 */
    @NotNull
    private final Long providerId;

    /** Access Key ID。 */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{16,128}")
    private final String accessKeyId;

    /** Secret Access Key，可见 ASCII，无空白。 */
    @NotBlank
    @Pattern(regexp = "[\\x21-\\x7E]{1,128}")
    private final String secretAccessKey;

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "ProtectNotificationCredentialRequest(providerId=" + providerId + ")";
    }
}
