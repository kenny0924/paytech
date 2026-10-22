package com.paytech.platform.support.filedocument.integration.storage;

import com.paytech.platform.support.api.enums.FileStorageCheckResult;

/**
 * 一个存储实例在一次操作中的完整适配器：上传授权、完成读取、下载授权、检查读取与存储桶检查
 * <li>由 {@link FileStorageRegistry} 按实例快照与本次凭据构建；调用方用完立即关闭，不在进程中保存带密钥的客户端。</li>
 * <li>{@link #providerCode()} 返回实例代码，即记录上的 storage_provider。</li>
 */
public interface FileObjectStorage
    extends UploadStorage, CompletedUploadStorage, FileDownloadStorage, StoredFileReader, AutoCloseable {

    /**
     * 存储桶启用检查：存在、版本控制已开启、默认加密已生效，并写入、读取、删除一个高熵探测对象
     * <li>探测对象位于 {@code .platform-readiness/} 前缀下，任何结果都尽力删除；不修改桶配置。</li>
     *
     * @param bucketName 存储桶名称
     * @return 第一项不满足的检查结果；全部满足为 OK
     */
    FileStorageCheckResult checkBucket(String bucketName);

    /** 释放本次构建的客户端与连接，不抛出受检异常。 */
    @Override
    void close();
}
