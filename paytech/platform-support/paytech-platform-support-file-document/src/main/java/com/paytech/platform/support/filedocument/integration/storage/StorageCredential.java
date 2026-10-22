package com.paytech.platform.support.filedocument.integration.storage;

/**
 * 一次存储操作使用的静态访问密钥；只在内存中存在，不缓存、不打印
 *
 * @param accessKeyId 访问账号
 * @param secretAccessKey 访问密钥
 */
public record StorageCredential(String accessKeyId, String secretAccessKey) {

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "StorageCredential[REDACTED]";
    }
}
