package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.ChangeStorageInstanceStatusCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.ChangeStorageInstanceStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本切换存储实例状态
 * <li>DRAFT→ACTIVE 要求至少一个 ACTIVE 存储桶；ACTIVE→DISABLED 要求没有路由指向其存储桶；DISABLED→ACTIVE。</li>
 * <li>停用后本进程立即不再读写该实例，其他实例最迟在快照刷新间隔后生效。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeStorageInstanceStatusProcessor
    extends AbstractLockProcessor<ChangeStorageInstanceStatusRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一实例的其他写入共用实例锁。 */
    @Override
    protected LockCommand lockCommand(ChangeStorageInstanceStatusRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(ChangeStorageInstanceStatusRequest request) {
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.changeInstanceStatus(new ChangeStorageInstanceStatusCommand(request.getInstanceCode(),
                request.getStatus(), request.getExpectedVersion(), now)));
    }
}
