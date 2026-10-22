package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileStorageBucketMapper;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageBucketRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的存储桶仓储。 */
@Repository
@AllArgsConstructor
public class FileStorageBucketRepositoryImpl implements FileStorageBucketRepository {

    private final FileStorageBucketMapper mapper;

    /** 全部存储桶。 */
    @Override
    public List<FileStorageBucket> findAll() {
        return mapper.selectAll();
    }

    /** 按实例与名称读取。 */
    @Override
    public FileStorageBucket findByName(long instanceId, String bucketName) {
        return mapper.selectByName(instanceId, bucketName);
    }

    /** 按实例与名称排他锁定。 */
    @Override
    public FileStorageBucket lockByName(long instanceId, String bucketName) {
        return mapper.lockByName(instanceId, bucketName);
    }

    /** 指定状态的存储桶数。 */
    @Override
    public int countByStatus(long instanceId, FileStorageBucketStatus status) {
        return mapper.countByStatus(instanceId, status);
    }

    /** 只把同实例同名唯一键冲突转换为“已登记”业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(FileStorageBucket bucket) {
        try {
            requireSaved(mapper.insert(bucket), bucket.getBucketName());
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_file_storage_bucket")) {
                throw failure;
            }
            throw new BizCheckFailException("存储桶已登记: " + bucket.getBucketName(), failure);
        }
    }

    /** 条件更新成功后同步检查结果、版本与时间。 */
    @Override
    public void updateCheck(FileStorageBucket bucket, FileStorageCheckResult result, Instant checkedAt) {
        requireSaved(mapper.updateCheck(bucket, result, checkedAt), bucket.getBucketName());
        bucket.setLastCheckResult(result);
        bucket.setLastCheckTime(checkedAt);
        bucket.setVersion(bucket.getVersion() + 1);
        bucket.setUpdateTime(checkedAt);
    }

    /** 条件更新成功后同步状态、版本与时间。 */
    @Override
    public void updateStatus(FileStorageBucket bucket, FileStorageBucketStatus status, Instant now) {
        requireSaved(mapper.updateStatus(bucket, status, now), bucket.getBucketName());
        bucket.setStatus(status);
        bucket.setVersion(bucket.getVersion() + 1);
        bucket.setUpdateTime(now);
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次对象。 */
    void requireSaved(int changed, String bucketName) {
        if (changed != 1) {
            throw new ConcurrentRequestException("存储桶已被其他操作修改，请刷新后重试: " + bucketName);
        }
    }
}
