package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.DeleteStorageRouteRequest;

import lombok.AllArgsConstructor;

/** 按原版本删除写入路由；默认路由 * 不能删除，删除后该来源的新上传使用默认路由。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteStorageRouteProcessor extends AbstractLockProcessor<DeleteStorageRouteRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一来源系统的新建、修改共用路由锁。 */
    @Override
    protected LockCommand lockCommand(DeleteStorageRouteRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.routeLockKey(request.getSourceSystem()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(DeleteStorageRouteRequest request) {
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.deleteRoute(request.getSourceSystem(), request.getExpectedVersion()));
    }
}
