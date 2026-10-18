package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient.Target;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadVerifier;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.service.processor.request.CompleteOnboardingFileRequest;
import com.paytech.platform.support.service.processor.response.CompleteOnboardingFileResponse;

import lombok.AllArgsConstructor;

/**
 * 完成入驻文件上传：锁定 Upload → 准入校验 → 事务外校验存储 → 重新授权 → 登记文件与扫描任务。
 * <li>资源锁按 uploadId 串行同一上传的完成请求；账号行锁、Upload 当前读和状态 CAS 继续保护过期 Lease 的竞争窗口。</li>
 * <li>一次上传只产生一个文件：已完成时返回原文件回执，不重复校验存储或登记扫描任务。</li>
 * <li>第一段事务只锁定并校验，不写业务数据；存储校验（外部 I/O）在两段事务之外。</li>
 * <li>存储 I/O 之后重新向 Owner 取得授权，不把旧申请许可带入登记事务。</li>
 * <li>第二段事务登记文件并注册扫描补偿任务，与业务变更同一事务。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class OnboardingFileCompletionProcessor
    extends AbstractLockProcessor<CompleteOnboardingFileRequest, CompleteOnboardingFileResponse> {

    private final Clock clock;

    private final IdGenerator ids;

    private final FileLifecycleService files;

    private final CompletedUploadVerifier verifier;

    private final CustomerAuthenticationService authentication;

    private final ClientOnboardingAccessService access;

    private final CctFileTargetClient targets;

    private final OperationsTransactionExecutor transactions;

    private final CompensationEventRegistrar events;

    /** 与原注解锁共用完整逻辑 Key、普通 RLock 和有限等待 / 租约。 */
    @Override
    protected LockCommand lockCommand(CompleteOnboardingFileRequest request) {
        return LockCommand.tryLocks(OnboardingFileLocks.completionKeys(request.getUploadId()),
            OnboardingFileLocks.WAIT_TIME, OnboardingFileLocks.LEASE_TIME);
    }

    /** 锁内按阶段完成上传；已完成的上传在第一段事务直接返回原回执。 */
    @Override
    protected CompleteOnboardingFileResponse processWithinLock(CompleteOnboardingFileRequest request) {
        // 读取归属
        Upload upload = transactions.query(() -> loadOwnedUpload(request));
        OwnerRef owner = OwnerRef.parse(upload.ownerRefId());
        Target target = targets.read(request.getAccountId(), owner.applicationId(), owner.subjectPartyId());

        // 锁定并校验准入
        CompleteOnboardingFileResponse previous = transactions.write(() -> lockForCompletion(request, upload, target));
        if (previous != null) {
            return previous;
        }

        // 事务外校验存储，再重新授权
        Verified verified = verifier.verify(upload);
        long clientId = targets.authorize(request.getAccountId(), owner.applicationId(), owner.subjectPartyId());

        // 登记文件
        return transactions.write(() -> registerCompletedFile(request, clientId, verified));
    }

    /** 事务内读取：校验会话后取回当前账号拥有的 Upload。 */
    Upload loadOwnedUpload(CompleteOnboardingFileRequest request) {
        authentication.context(request.getAccountId(), request.getSessionId());
        return files.owned(request.getUploadId(), request.getAccountId());
    }

    /**
     * 第一段事务：锁定 Upload 并校验准入。
     *
     * @return 已完成时返回原回执；否则返回 null，表示可以继续校验存储
     */
    CompleteOnboardingFileResponse lockForCompletion(CompleteOnboardingFileRequest request, Upload upload,
        Target target) {
        access.lockFileRead(request.getAccountId(), request.getSessionId(), target.clientId(), upload.tenantId());
        Upload current = files.lockOwned(request.getUploadId(), request.getAccountId(), upload.tenantId());

        Completed completed = files.completedFile(current);
        if (completed != null) {
            return response(request, completed.fileId(), completed.completedAt());
        }
        if (!target.canUpload()) {
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        }

        long tenantId = access.lockUploadTenant(request.getAccountId(), request.getSessionId(), target.clientId());
        if (tenantId != upload.tenantId()) {
            throw new FailException(OnboardingFileErrorCode.FILE_TARGET_CONFLICT);
        }

        files.requireOpen(current, clock.instant());
        return null;
    }

    /** 第二段事务：登记文件、注册扫描任务并返回回执；并发下已完成则返回原回执。 */
    CompleteOnboardingFileResponse registerCompletedFile(CompleteOnboardingFileRequest request, long clientId,
        Verified verified) {
        long tenantId = access.lockUploadTenant(request.getAccountId(), request.getSessionId(), clientId);
        Upload current = files.lockOwned(request.getUploadId(), request.getAccountId(), tenantId);

        Completed completed = files.completedFile(current);
        if (completed != null) {
            return response(request, completed.fileId(), completed.completedAt());
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long fileId = ids.nextId();
        files.complete(fileId, ids.nextId(), current, verified, now);
        events.register(FileScanHandler.buildEvent(fileId));
        return response(request, fileId, now);
    }

    /** 完成操作身份即 uploadId，首次与重复完成返回同一组值。 */
    CompleteOnboardingFileResponse response(CompleteOnboardingFileRequest request, long fileId, Instant completedAt) {
        return new CompleteOnboardingFileResponse(request.getUploadId(), fileId, completedAt);
    }

    /**
     * Upload 的 Owner 引用，格式 {@code <applicationId>:<subjectPartyId>}。
     *
     * @param applicationId CCT 入驻申请 ID
     * @param subjectPartyId 申请下的主体 Party ID
     */
    record OwnerRef(long applicationId, long subjectPartyId) {

        /** 解析 ownerRefId；该值由本服务在上传开始时写入，格式不符属于数据错误，与原实现一致抛出 NumberFormatException。 */
        static OwnerRef parse(String ownerRefId) {
            String[] parts = ownerRefId.split(":", -1);
            return new OwnerRef(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        }
    }
}
