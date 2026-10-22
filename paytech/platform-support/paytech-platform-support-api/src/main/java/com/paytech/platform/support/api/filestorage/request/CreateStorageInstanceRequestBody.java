package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新建 DRAFT 存储实例；STATIC 的密钥先存入 Vault 再写库，实例代码与类型创建后不可修改。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateStorageInstanceRequestBody {

    /**
     * 实例代码
     * <li>[A-Z][A-Z0-9_]{0,63}；即文件记录上的存储提供方，创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String instanceCode;

    /**
     * 存储类型
     * <li>{@code FileStorageType} Code：MINIO、S3。</li>
     */
    @NotBlank
    @Pattern(regexp = "MINIO|S3")
    private String storageType;

    /**
     * HTTPS 根地址
     * <li>S3 可空（使用区域默认地址）；MINIO 必填。同时是浏览器直传与下载的地址，不能带路径、查询或用户信息。</li>
     */
    @Size(max = 255)
    private String endpoint;

    /** 签名区域，例如 us-east-1。 */
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
    private String region;

    /** 是否使用路径风格访问；兼容 S3 的存储通常为 true。 */
    @NotNull
    private Boolean pathStyleAccess;

    /**
     * 私有 CA 或自签名证书 PEM
     * <li>可空；为空时使用 JDK 默认信任库，非空时只信任该证书。最多 16384 字符。</li>
     */
    @Size(max = 16384)
    private String trustedCertificatePem;

    /**
     * 连接超时毫秒数
     * <li>1000—10000；为空取 2000。</li>
     */
    @Min(1000)
    @Max(10000)
    private Integer connectTimeoutMs;

    /**
     * 读写超时毫秒数
     * <li>1000—60000；为空取 10000。</li>
     */
    @Min(1000)
    @Max(60000)
    private Integer readTimeoutMs;

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
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 不输出密钥与证书。 */
    @Override
    public String toString() {
        return "CreateStorageInstanceRequestBody[REDACTED]";
    }
}
