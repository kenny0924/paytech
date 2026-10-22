package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 存储实例列表的一项：实例、其存储桶与引用该实例的路由数；不含 Vault Token。 */
@Getter
@AllArgsConstructor
public class FileStorageInstanceView {

    /** 实例代码。 */
    private final String instanceCode;

    /** 存储类型 Code。 */
    private final String storageType;

    /** 服务地址；可空。 */
    private final String endpoint;

    /** 签名区域。 */
    private final String region;

    /** 是否使用路径风格访问。 */
    private final boolean pathStyleAccess;

    /** 私有证书 PEM；可空。 */
    private final String trustedCertificatePem;

    /** 凭据方式 Code。 */
    private final String credentialMode;

    /** 是否已保存密钥 Token。 */
    private final boolean credentialConfigured;

    /** 最近一次录入或更换密钥的时间；可空。 */
    private final Instant credentialUpdateTime;

    /** 连接超时毫秒数。 */
    private final int connectTimeoutMs;

    /** 读写超时毫秒数。 */
    private final int readTimeoutMs;

    /** 状态 Code。 */
    private final String status;

    /** 乐观并发版本。 */
    private final int version;

    /** 最后更新时间。 */
    private final Instant updateTime;

    /** 引用该实例存储桶的写入路由数。 */
    private final int routeCount;

    /** 实例下的存储桶，按名称排序。 */
    private final List<FileStorageBucketView> buckets;
}
