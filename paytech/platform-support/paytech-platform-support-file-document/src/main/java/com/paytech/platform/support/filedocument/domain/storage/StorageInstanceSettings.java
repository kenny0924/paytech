package com.paytech.platform.support.filedocument.domain.storage;

import com.paytech.platform.support.api.enums.FileStorageType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 存储实例的连接设置；新建时完整提供，编辑时类型不可变
 * <li>地址与证书格式、超时范围由 Service 在保存前校验。</li>
 */
@Getter
@AllArgsConstructor
public class StorageInstanceSettings {

    /** 存储类型。 */
    private final FileStorageType storageType;

    /** HTTPS 根地址；S3 可空。 */
    private final String endpoint;

    /** 签名区域。 */
    private final String region;

    /** 是否使用路径风格访问。 */
    private final boolean pathStyleAccess;

    /** 私有 CA 或自签名证书 PEM；可空。 */
    private final String trustedCertificatePem;

    /** 连接超时毫秒数。 */
    private final int connectTimeoutMs;

    /** 读写超时毫秒数。 */
    private final int readTimeoutMs;

    /** 不输出证书原文。 */
    @Override
    public String toString() {
        return "StorageInstanceSettings[" + storageType + "]";
    }
}
