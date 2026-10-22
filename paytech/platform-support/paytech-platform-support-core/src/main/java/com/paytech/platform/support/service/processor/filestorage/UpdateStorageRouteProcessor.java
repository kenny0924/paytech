package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.SaveStorageRouteCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.SaveStorageRouteRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改写入路由；只影响之后的新上传，已有上传与文件继续使用记录上的实例与桶
 * <li>目标资格同新建；无变化不递增版本。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateStorageRouteProcessor extends AbstractLockProcessor<SaveStorageRouteRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一来源系统的新建、删除共用路由锁。 */
    @Override
    protected LockCommand lockCommand(SaveStorageRouteRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.routeLockKey(request.getSourceSystem()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(SaveStorageRouteRequest request) {
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.updateRoute(new SaveStorageRouteCommand(0, request.getSourceSystem(),
                request.getInstanceCode(), request.getBucketName(), request.getScanPolicyCode(),
                request.getMaxUploadSeconds(), request.getExpectedVersion(), now)));
    }
}
