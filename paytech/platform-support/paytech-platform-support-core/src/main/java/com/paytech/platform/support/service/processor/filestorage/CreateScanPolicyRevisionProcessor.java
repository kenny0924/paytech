package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.SaveScanPolicyRevisionCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileScanPolicyService;
import com.paytech.platform.support.service.processor.filestorage.request.SaveScanPolicyRequest;

import lombok.AllArgsConstructor;

/**
 * 以当前 ACTIVE 修订为模板新建 DRAFT 修订
 * <li>按策略代码持资源锁；没有 ACTIVE 返回 400，已有 DRAFT 返回 409。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateScanPolicyRevisionProcessor extends AbstractLockProcessor<SaveScanPolicyRequest, FileStorageChange> {

    private final IdGenerator ids;
    private final FileScanPolicyService policies;
    private final FileStorageManagementSupport support;

    /** 同一策略代码的全部修订写入共用策略锁。 */
    @Override
    protected LockCommand lockCommand(SaveScanPolicyRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.scanPolicyLockKey(request.getPolicyCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(SaveScanPolicyRequest request) {
        Instant now = support.now();
        long policyId = ids.nextId();
        long requirementId = ids.nextId();
        SaveScanPolicyRevisionCommand command = new SaveScanPolicyRevisionCommand(policyId, requirementId,
            request.getPolicyCode(), request.getRevisionNo(), request.getAllowedMimeTypes(), request.getMaxSizeBytes(),
            request.getExpectedVersion(), now);
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> policies.createRevision(command));
    }
}
