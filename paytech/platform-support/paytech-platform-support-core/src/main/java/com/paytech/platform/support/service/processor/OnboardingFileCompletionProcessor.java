package com.paytech.platform.support.service.processor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileCompletionReceipt;
import com.paytech.platform.support.accesscontrol.domainservice.service.*;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadVerifier;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.service.handler.FileScanHandler;
/** 上传完成编排；原授权、实际字节和本地原子登记的边界均显式可见。 */
@Component @lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingFileCompletionProcessor {
    private final Clock clock;
    private final IdGenerator ids;
    private final FileLifecycleService files;
    private final CompletedUploadVerifier verifier;
    private final CustomerAuthenticationService authentication;
    private final ClientOnboardingAccessService access;
    private final CctFileTargetClient targets;
    private final OperationsTransactionExecutor transactions;
    private final CompensationEventRegistrar events;
    /**
     * 锁实际 Upload；账号行锁、Upload 当前读和状态 CAS 继续保护过期 Lease 的竞争窗口
     * <li>一次上传只产生一个文件：已完成时返回原文件，不重复校验存储或登记扫描任务。</li>
     */
    @ResourceLock(prefix="file:upload:complete",key="#uploadId")
    public FileCompletionReceipt complete(long accountId,long sessionId,long uploadId) {
        var upload=transactions.query(() -> { authentication.context(accountId,sessionId);return files.owned(uploadId,accountId); });
        String[] owner=upload.ownerRefId().split(":",-1);
        var target=targets.read(accountId,Long.parseLong(owner[0]),Long.parseLong(owner[1]));
        var previous=transactions.write(() -> {
            access.lockFileRead(accountId,sessionId,target.clientId(),upload.tenantId());
            var current=files.lockOwned(uploadId,accountId,upload.tenantId());
            var completed=files.completedFile(current);
            if (completed!=null) return receipt(uploadId,completed.fileId(),completed.completedAt());
            if (!target.canUpload()) throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
            long tenant=access.lockUploadTenant(accountId,sessionId,target.clientId());
            if (tenant!=upload.tenantId()) throw new FailException(OnboardingFileErrorCode.FILE_TARGET_CONFLICT);
            files.requireOpen(current,clock.instant());return null;
        });
        if (previous!=null) return previous;
        var verified=verifier.verify(upload);
        // 存储 I/O 之后重新询问原 Owner，不能把旧申请许可带入登记事务。
        long client=targets.authorize(accountId,Long.parseLong(owner[0]),Long.parseLong(owner[1]));
        return transactions.write(() -> {
            long tenant=access.lockUploadTenant(accountId,sessionId,client);
            var current=files.lockOwned(uploadId,accountId,tenant);
            var completed=files.completedFile(current);
            if (completed!=null) return receipt(uploadId,completed.fileId(),completed.completedAt());
            var now=clock.instant().truncatedTo(ChronoUnit.MICROS);long fileId=ids.nextId();
            files.complete(fileId,ids.nextId(),current,verified,now);
            events.register(FileScanHandler.buildEvent(fileId));
            return receipt(uploadId,fileId,now);
        });
    }
    /** 完成操作身份即 uploadId，重复完成返回同一回执。 */
    FileCompletionReceipt receipt(long uploadId,long fileId,Instant completedAt) {
        return new FileCompletionReceipt(Long.toString(uploadId),Long.toString(uploadId),Long.toString(fileId),completedAt);
    }
}
