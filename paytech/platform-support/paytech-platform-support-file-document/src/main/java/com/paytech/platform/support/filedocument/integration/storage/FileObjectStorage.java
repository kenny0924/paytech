package com.paytech.platform.support.filedocument.integration.storage;

import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;

/**
 * 一个存储实例在一次操作中的完整适配器：上传授权、完成读取、下载授权、检查读取与存储桶检查
 * <li>由 {@link FileStorageRegistry} 按实例快照与本次凭据构建；调用方用完立即关闭，不在进程中保存带密钥的客户端。</li>
 * <li>{@link #providerCode()} 返回实例代码，即记录上的 storage_provider。</li>
 */
public interface FileObjectStorage
    extends UploadStorage, CompletedUploadStorage, FileDownloadStorage, StoredFileReader, AutoCloseable {

    /**
     * 存储桶启用检查：存在、版本控制已开启、默认加密已生效，并写入、读取、匿名访问核对、删除一个高熵探测对象
     * <li>探测对象位于 {@code .platform-readiness/} 前缀下，任何结果都尽力删除；不修改桶配置。</li>
     * <li>匿名访问：PRIVATE 不带凭据读取存储地址上的探测对象必须被拒绝；PUBLIC 经对外基础地址匿名读取必须得到原字节。</li>
     *
     * @param bucketName 存储桶名称
     * @param accessLevel 存储桶访问级别
     * @param publicBaseUrl 公开桶的对外基础地址；私有桶为空
     * @return 第一项不满足的检查结果；全部满足为 OK
     */
    FileStorageCheckResult checkBucket(String bucketName, FileAccessLevel accessLevel, String publicBaseUrl);

    /**
     * 读取对象最新版本的身份（含 Platform 恢复复制写入的源版本元数据）
     *
     * @param container 存储桶
     * @param objectRef 对象名
     * @return 最新版本
     */
    LatestVersion latest(String container, String objectRef);

    /**
     * 把文件当前版本复制为该对象的最新版本（公开恢复），写入源版本元数据并保留当前版本的 Content-Type
     *
     * @param current 文件当前版本坐标
     */
    void restore(File current);

    /** 释放本次构建的客户端与连接，不抛出受检异常。 */
    @Override
    void close();
}
