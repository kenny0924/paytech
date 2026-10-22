package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.RegisterStorageBucketCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.RegisterStorageBucketRequest;

import lombok.AllArgsConstructor;

/**
 * 在存储实例下登记 DRAFT 存储桶；Platform 不建桶
 * <li>按实例代码持资源锁；同实例同名唯一索引为最终保护。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class RegisterStorageBucketProcessor
    extends AbstractLockProcessor<RegisterStorageBucketRequest, FileStorageChange> {

    private final IdGenerator ids;
    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一实例的其他写入共用实例锁。 */
    @Override
    protected LockCommand lockCommand(RegisterStorageBucketRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(RegisterStorageBucketRequest request) {
        Instant now = support.now();
        long bucketId = ids.nextId();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.registerBucket(new RegisterStorageBucketCommand(bucketId, request.getInstanceCode(),
                request.getBucketName(), now)));
    }
}
