package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本修改存储实例的连接设置；类型、代码与凭据另有入口，不在此修改。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStorageInstanceRequestBody {

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
        return "UpdateStorageInstanceRequestBody[REDACTED]";
    }
}
