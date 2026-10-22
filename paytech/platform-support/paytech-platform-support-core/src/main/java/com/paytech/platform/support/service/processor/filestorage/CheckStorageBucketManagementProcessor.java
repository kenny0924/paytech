package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.RecordStorageBucketCheckCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.filedocument.service.processor.CheckStorageBucketProcessor;
import com.paytech.platform.support.filedocument.service.processor.request.CheckStorageBucketRequest;
import com.paytech.platform.support.service.processor.filestorage.request.CheckStorageBucketManagementRequest;
import com.paytech.platform.support.service.processor.filestorage.response.StorageBucketCheckOutcome;

import lombok.AllArgsConstructor;

/**
 * 检查存储桶并回写结果：鉴权 → 事务外用实例当前地址与密钥检查 → 短事务回写结果并审计
 * <li>按实例代码持资源锁；实例为 DRAFT 也可检查。Vault 不可用返回 503 且不回写，存储侧问题作为结果回写。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CheckStorageBucketManagementProcessor
    extends AbstractLockProcessor<CheckStorageBucketManagementRequest, StorageBucketCheckOutcome> {

    /** 检查结果回写的审计原因。 */
    static final String CHECK_REASON = "存储桶启用检查";

    private final CheckStorageBucketProcessor check;
    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    /** 与同一实例的其他写入共用实例锁，锁覆盖存储检查。 */
    @Override
    protected LockCommand lockCommand(CheckStorageBucketManagementRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected StorageBucketCheckOutcome processWithinLock(CheckStorageBucketManagementRequest request) {
        // 鉴权
        support.precheck(request.getActor(), request.getSession(), () -> null);

        // 事务外检查
        FileStorageCheckResult result =
            check.process(new CheckStorageBucketRequest(request.getInstanceCode(), request.getBucketName()));

        // 短事务回写结果并审计
        Instant now = support.now();
        FileStorageChange change = support.write(request.getActor(), request.getSession(), CHECK_REASON, now,
            () -> management.recordBucketCheck(new RecordStorageBucketCheckCommand(request.getInstanceCode(),
                request.getBucketName(), result, now)));
        return new StorageBucketCheckOutcome(change, result, now);
    }
}
