package com.paytech.platform.support.filedocument.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 把存储实例的静态访问密钥存入 Vault；密钥只在内存中传递。 */
@Getter
@AllArgsConstructor
public class ProtectStorageCredentialRequest {

    /** 存储实例主键，作为 Vault 幂等键的一部分。 */
    @Positive(message = "存储实例 ID 必须为正整数")
    private final long instanceId;

    /** 访问账号。 */
    @NotBlank(message = "accessKey 必填")
    @Size(max = 256, message = "accessKey 最多 256 个字符")
    private final String accessKeyId;

    /** 访问密钥，不进入日志。 */
    @NotBlank(message = "secretKey 必填")
    @Size(max = 512, message = "secretKey 最多 512 个字符")
    private final String secretAccessKey;

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "ProtectStorageCredentialRequest[" + instanceId + "]";
    }
}
