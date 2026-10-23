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
 * 按原版本废弃 DRAFT 修订：物理删除修订及其检查要求（草稿从未被上传冻结）
 * <li>按策略代码持资源锁；非 DRAFT 返回 400。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DiscardScanPolicyRevisionProcessor extends AbstractLockProcessor<ChangeScanPolicyRevisionRequest, FileStorageChange> {

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
            () -> policies.discardRevision(command));
    }
}
