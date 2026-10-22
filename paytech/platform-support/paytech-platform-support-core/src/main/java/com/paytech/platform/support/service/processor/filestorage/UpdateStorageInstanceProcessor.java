package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.UpdateStorageInstanceCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.UpdateStorageInstanceRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改存储实例的地址、区域、路径风格、证书与超时
 * <li>按实例代码持资源锁，同一事务内鉴权、行锁、版本核对、保存与审计；无变化不递增版本。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateStorageInstanceProcessor
    extends AbstractLockProcessor<UpdateStorageInstanceRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 地址与证书格式只依赖输入。 */
    @Override
    protected void validate(UpdateStorageInstanceRequest request) {
        FileStorageManagementSupport.requireSettings(request.getSettings());
    }

    /** 与同一实例的其他写入共用实例锁。 */
    @Override
    protected LockCommand lockCommand(UpdateStorageInstanceRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(UpdateStorageInstanceRequest request) {
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.updateInstance(new UpdateStorageInstanceCommand(request.getInstanceCode(),
                request.getSettings(), request.getExpectedVersion(), now)));
    }
}
