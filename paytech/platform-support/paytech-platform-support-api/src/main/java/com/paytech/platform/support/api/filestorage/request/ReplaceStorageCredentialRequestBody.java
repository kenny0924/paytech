package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 更换存储实例密钥或切换凭据方式；新密钥先存入 Vault，再以短事务替换 Token。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceStorageCredentialRequestBody {

    /**
     * 凭据方式
     * <li>{@code FileStorageCredentialMode} Code：STATIC（密钥存入 Vault）、IAM_ROLE（默认凭据链，只用于 S3）。</li>
     */
    @NotBlank
    @Pattern(regexp = "STATIC|IAM_ROLE")
    private String credentialMode;

    /**
     * 访问账号
     * <li>STATIC 必填，IAM_ROLE 必须为空；只存入 Vault，不落库、不进日志与审计。</li>
     */
    @Size(max = 256)
    private String accessKey;

    /**
     * 访问密钥
     * <li>STATIC 必填，IAM_ROLE 必须为空；只存入 Vault，不落库、不进日志与审计。</li>
     */
    @Size(max = 512)
    private String secretKey;

    /**
     * 调用方读取的原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /**
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 不输出密钥与证书。 */
    @Override
    public String toString() {
        return "ReplaceStorageCredentialRequestBody[REDACTED]";
    }
}
