package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.ChangeScanPolicyRevisionCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domainservice.service.FileScanPolicyService;
import com.paytech.platform.support.service.processor.filestorage.request.ChangeScanPolicyRevisionRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本激活 DRAFT 修订：原 ACTIVE 修订改为 RETIRED、草稿改为 ACTIVE，同一事务完成
 * <li>按策略代码持资源锁；已开始的上传冻结了原修订不受影响，新修订只影响之后的上传。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ActivateScanPolicyRevisionProcessor extends AbstractLockProcessor<ChangeScanPolicyRevisionRequest, FileStorageChange> {

    private final FileScanPolicyService policies;
    private final FileStorageManagementSupport support;

    /** 同一策略代码的全部修订写入共用策略锁。 */
    @Override
    protected LockCommand lockCommand(ChangeScanPolicyRevisionRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.scanPolicyLockKey(request.getPolicyCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(ChangeScanPolicyRevisionRequest request) {
        Instant now = support.now();
        ChangeScanPolicyRevisionCommand command = new ChangeScanPolicyRevisionCommand(request.getPolicyCode(),
            request.getRevisionNo(), request.getExpectedVersion(), now);
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> policies.activateRevision(command));
    }
}
