package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.UpdateStorageBucketCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.UpdateStorageBucketRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改 DRAFT 公开桶的对外基础地址
 * <li>按实例代码持资源锁；地址变化时清除最近检查结果，需重新检查才能启用；启用后地址不可再改（换域名另立需求）。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateStorageBucketProcessor extends AbstractLockProcessor<UpdateStorageBucketRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 基础地址格式只依赖输入。 */
    @Override
    protected void validate(UpdateStorageBucketRequest request) {
        FileStorageManagementSupport.requirePublicBaseUrl(FileAccessLevel.PUBLIC, request.getPublicBaseUrl());
    }

    /** 与同一实例的其他写入共用实例锁。 */
    @Override
    protected LockCommand lockCommand(UpdateStorageBucketRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(UpdateStorageBucketRequest request) {
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.updateBucket(new UpdateStorageBucketCommand(request.getInstanceCode(),
                request.getBucketName(), request.getPublicBaseUrl(), request.getExpectedVersion(), now)));
    }
}
