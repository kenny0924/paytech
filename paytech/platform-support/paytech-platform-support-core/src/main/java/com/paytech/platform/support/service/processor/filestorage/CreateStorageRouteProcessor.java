package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.SaveStorageRouteCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.SaveStorageRouteRequest;

import lombok.AllArgsConstructor;

/**
 * 新建写入路由
 * <li>按来源系统持资源锁；目标实例、存储桶与策略生效修订在事务内按实例 → 桶 → 策略顺序行锁后核对（含桶与路由同访问级别），与停用互斥。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateStorageRouteProcessor extends AbstractLockProcessor<SaveStorageRouteRequest, FileStorageChange> {

    private final IdGenerator ids;
    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一来源系统的修改、删除共用路由锁。 */
    @Override
    protected LockCommand lockCommand(SaveStorageRouteRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.routeLockKey(request.getSourceSystem()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(SaveStorageRouteRequest request) {
        Instant now = support.now();
        long routeId = ids.nextId();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.createRoute(new SaveStorageRouteCommand(routeId, request.getSourceSystem(),
                request.getAccessLevel(), request.getInstanceCode(), request.getBucketName(), request.getScanPolicyCode(),
                request.getMaxUploadSeconds(), 0, now)));
    }
}
