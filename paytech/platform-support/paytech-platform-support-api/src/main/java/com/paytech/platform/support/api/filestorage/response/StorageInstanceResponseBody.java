package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一个存储实例及其存储桶；不返回 Vault Token 或密钥，只返回是否已配置密钥。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageInstanceResponseBody {

    /** 实例代码，即文件记录上的存储提供方。 */
    private String instanceCode;

    /**
     * 存储类型
     * <li>{@code FileStorageType} Code：MINIO、S3。</li>
     */
    private String storageType;

    /** HTTPS 根地址；S3 为空时使用区域默认地址。 */
    private String endpoint;

    /** 签名区域。 */
    private String region;

    /** 是否使用路径风格访问。 */
    private boolean pathStyleAccess;

    /** 私有 CA 或自签名证书 PEM（公开内容）；可空。 */
    private String trustedCertificatePem;

    /**
     * 凭据方式
     * <li>{@code FileStorageCredentialMode} Code：STATIC、IAM_ROLE。</li>
     */
    private String credentialMode;

    /** 是否已在 Vault 保存静态密钥。 */
    private boolean credentialConfigured;

    /** 最近一次录入或更换密钥的时间（UTC）；可空。 */
    private Instant credentialUpdateTime;

    /** 连接超时毫秒数。 */
    private int connectTimeoutMs;

    /** 读写超时毫秒数。 */
    private int readTimeoutMs;

    /**
     * 状态
     * <li>{@code FileStorageInstanceStatus} Code：DRAFT、ACTIVE、DISABLED。</li>
     */
    private String status;

    /** 乐观并发版本。 */
    private int version;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;

    /** 指向该实例存储桶的写入路由数；大于 0 时不能停用实例。 */
    private int routeCount;

    /** 实例下的存储桶，按名称排序。 */
    private List<StorageBucket> buckets;
}
