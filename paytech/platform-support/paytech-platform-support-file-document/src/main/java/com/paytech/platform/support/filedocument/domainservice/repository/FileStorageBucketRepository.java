package com.paytech.platform.support.filedocument.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;

/** 存储桶表仓储；条件更新成功后同步传入的原对象。 */
public interface FileStorageBucketRepository {

    /** 全部存储桶，按实例与名称排序。 */
    List<FileStorageBucket> findAll();

    /** 按实例与名称读取；不存在为空。 */
    FileStorageBucket findByName(long instanceId, String bucketName);

    /** 按实例与名称排他锁定；不存在为空。 */
    FileStorageBucket lockByName(long instanceId, String bucketName);

    /** 实例下指定状态的存储桶数。 */
    int countByStatus(long instanceId, FileStorageBucketStatus status);

    /** 插入新存储桶；同实例同名已存在转换为业务拒绝。 */
    void insert(FileStorageBucket bucket);

    /** 回写检查结果并递增版本。 */
    void updateCheck(FileStorageBucket bucket, FileStorageCheckResult result, Instant checkedAt);

    /** 按原版本与原状态切换状态。 */
    void updateStatus(FileStorageBucket bucket, FileStorageBucketStatus status, Instant now);
}
