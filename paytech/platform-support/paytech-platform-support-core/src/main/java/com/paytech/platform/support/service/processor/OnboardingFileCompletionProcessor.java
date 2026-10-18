package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileCompletionReceipt;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient.Target;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadVerifier;
import com.paytech.platform.support.service.handler.FileScanHandler;

import lombok.AllArgsConstructor;

/**
 * 上传完成编排；原授权、实际字节和本地原子登记的边界均显式可见。
 * <li>第一段事务只锁定 Upload 并做准入校验，不写业务数据；存储校验在事务外完成。</li>
 * <li>存储 I/O 之后重新向 Owner 取得授权，不把旧申请许可带入登记事务。</li>
 * <li>第二段事务登记文件并注册扫描任务，与业务变更同一事务。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
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
     * 锁实际 Upload；账号行锁、Upload 当前读和状态 CAS 继续保护过期 Lease 的竞争窗口。
     * <li>一次上传只产生一个文件：已完成时返回原文件，不重复校验存储或登记扫描任务。</li>
     *
     * @param accountId 当前账号
     * @param sessionId 当前会话
     * @param uploadId 上传 ID
     * @return 完成回执；重复完成返回同一回执
     */
    @ResourceLock(prefix = "file:upload:complete", key = "#uploadId")
    public FileCompletionReceipt complete(long accountId, long sessionId, long uploadId) {
        Upload upload = transactions.query(() -> loadOwnedUpload(accountId, sessionId, uploadId));
        OwnerRef owner = OwnerRef.parse(upload.ownerRefId());
        Target target = targets.read(accountId, owner.applicationId(), owner.subjectPartyId());

        FileCompletionReceipt previous = transactions.write(
            () -> lockForCompletion(accountId, sessionId, uploadId, upload, target));
        if (previous != null) {
            return previous;
        }

        Verified verified = verifier.verify(upload);
        // 存储 I/O 之后重新询问原 Owner，不能把旧申请许可带入登记事务。
        long clientId = targets.authorize(accountId, owner.applicationId(), owner.subjectPartyId());

        return transactions.write(() -> registerCompletedFile(accountId, sessionId, uploadId, clientId, verified));
    }

    /** 事务内读取：校验会话后取回当前账号拥有的 Upload。 */
    private Upload loadOwnedUpload(long accountId, long sessionId, long uploadId) {
        authentication.context(accountId, sessionId);
        return files.owned(uploadId, accountId);
    }

    /**
     * 第一段事务：锁定 Upload 并校验准入。
     *
     * @return 已完成时返回原回执；否则返回 null，表示可以继续校验存储
     */
    private FileCompletionReceipt lockForCompletion(long accountId, long sessionId, long uploadId, Upload upload,
        Target target) {
        access.lockFileRead(accountId, sessionId, target.clientId(), upload.tenantId());
        Upload current = files.lockOwned(uploadId, accountId, upload.tenantId());

        Completed completed = files.completedFile(current);
        if (completed != null) {
            return receipt(uploadId, completed.fileId(), completed.completedAt());
        }
        if (!target.canUpload()) {
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        }

        long tenantId = access.lockUploadTenant(accountId, sessionId, target.clientId());
        if (tenantId != upload.tenantId()) {
            throw new FailException(OnboardingFileErrorCode.FILE_TARGET_CONFLICT);
        }

        files.requireOpen(current, clock.instant());
        return null;
    }

    /** 第二段事务：登记文件、注册扫描任务并返回回执；并发下已完成则返回原回执。 */
    private FileCompletionReceipt registerCompletedFile(long accountId, long sessionId, long uploadId, long clientId,
        Verified verified) {
        long tenantId = access.lockUploadTenant(accountId, sessionId, clientId);
        Upload current = files.lockOwned(uploadId, accountId, tenantId);

        Completed completed = files.completedFile(current);
        if (completed != null) {
            return receipt(uploadId, completed.fileId(), completed.completedAt());
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long fileId = ids.nextId();
        files.complete(fileId, ids.nextId(), current, verified, now);
        events.register(FileScanHandler.buildEvent(fileId));
        return receipt(uploadId, fileId, now);
    }

    /** 完成操作身份即 uploadId，重复完成返回同一回执。 */
    FileCompletionReceipt receipt(long uploadId, long fileId, Instant completedAt) {
        return new FileCompletionReceipt(Long.toString(uploadId), Long.toString(uploadId), Long.toString(fileId),
            completedAt);
    }

    /**
     * Upload 的 Owner 引用，格式 {@code <applicationId>:<subjectPartyId>}。
     *
     * @param applicationId CCT 入驻申请 ID
     * @param subjectPartyId 申请下的主体 Party ID
     */
    record OwnerRef(long applicationId, long subjectPartyId) {

        /** 解析 ownerRefId；格式不符时与原实现一致抛出 NumberFormatException。 */
        static OwnerRef parse(String ownerRefId) {
            String[] parts = ownerRefId.split(":", -1);
            return new OwnerRef(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        }
    }
}
