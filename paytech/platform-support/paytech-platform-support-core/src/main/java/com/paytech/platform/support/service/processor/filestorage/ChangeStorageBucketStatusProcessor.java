package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.ChangeStorageBucketStatusCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.ChangeStorageBucketStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本切换存储桶状态
 * <li>DRAFT→ACTIVE 要求 10 分钟内检查为 OK；ACTIVE↔READ_ONLY；ACTIVE / READ_ONLY→DISABLED；有路由指向时不能只读或停用。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeStorageBucketStatusProcessor
    extends AbstractLockProcessor<ChangeStorageBucketStatusRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一实例的其他写入共用实例锁。 */
    @Override
    protected LockCommand lockCommand(ChangeStorageBucketStatusRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(ChangeStorageBucketStatusRequest request) {
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.changeBucketStatus(new ChangeStorageBucketStatusCommand(request.getInstanceCode(),
                request.getBucketName(), request.getStatus(), request.getExpectedVersion(), now)));
    }
}
