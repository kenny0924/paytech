package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.SaveScanPolicyRevisionCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileScanPolicyService;
import com.paytech.platform.support.service.processor.filestorage.request.SaveScanPolicyRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改 DRAFT 修订的允许类型与最大字节数
 * <li>按策略代码持资源锁；ACTIVE 与 RETIRED 修订不可修改，无变化时版本不变。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateScanPolicyRevisionProcessor extends AbstractLockProcessor<SaveScanPolicyRequest, FileStorageChange> {

    private final FileScanPolicyService policies;
    private final FileStorageManagementSupport support;

    /** 允许类型与最大字节数只依赖输入。 */
    @Override
    protected void validate(SaveScanPolicyRequest request) {
        FileStorageManagementSupport.requireRevisionContent(request.getAllowedMimeTypes(), request.getMaxSizeBytes());
    }

    /** 同一策略代码的全部修订写入共用策略锁。 */
    @Override
    protected LockCommand lockCommand(SaveScanPolicyRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.scanPolicyLockKey(request.getPolicyCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(SaveScanPolicyRequest request) {
        Instant now = support.now();
        SaveScanPolicyRevisionCommand command = new SaveScanPolicyRevisionCommand(0, 0,
            request.getPolicyCode(), request.getRevisionNo(), request.getAllowedMimeTypes(), request.getMaxSizeBytes(),
            request.getExpectedVersion(), now);
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> policies.updateRevision(command));
    }
}
