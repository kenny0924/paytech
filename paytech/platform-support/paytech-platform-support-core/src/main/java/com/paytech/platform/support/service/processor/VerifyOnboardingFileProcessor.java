package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Owned;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.service.processor.request.VerifyOnboardingFileRequest;
import com.paytech.platform.support.service.processor.response.VerifyOnboardingFileResponse;

import lombok.AllArgsConstructor;

/**
 * CCT 关联入驻文件前的私网核验：在同一只读快照核对原账号、原申请主体、原 Tenant 与当前文件事实。
 * <li>CCT 已自行核验申请与主体；本入口不回调 CCT，避免循环调用。</li>
 * <li>纯查询，不写业务数据、不加锁、不签发下载 URL；任何归属不符都按不存在处理。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class VerifyOnboardingFileProcessor
    extends AbstractProcessor<VerifyOnboardingFileRequest, VerifyOnboardingFileResponse> {

    private final Clock clock;

    private final FileLifecycleService files;

    private final ClientOnboardingAccessService access;

    private final OperationsTransactionExecutor transactions;

    @Override
    protected VerifyOnboardingFileResponse processRequest(VerifyOnboardingFileRequest request) {
        return transactions.query(() -> verifyWithinSnapshot(request));
    }

    /** 事务内：读取原账号上传的文件，核对期望申请主体与原 Tenant 后构造证据。 */
    VerifyOnboardingFileResponse verifyWithinSnapshot(VerifyOnboardingFileRequest request) {
        Owned owned = files.read(request.getFileId(), request.getAccountId());
        if (owned.applicationId() != request.getApplicationId()
            || owned.subjectPartyId() != request.getSubjectPartyId()) {
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        }
        access.readFile(request.getAccountId(), request.getClientId(), owned.upload().tenantId());
        return response(owned);
    }

    /** 从当前文件事实构造最小证据，核验时间取本次快照内的当前时间。 */
    VerifyOnboardingFileResponse response(Owned owned) {
        File file = owned.file();
        return new VerifyOnboardingFileResponse(file.fileId(), owned.upload().uploadId(),
            file.verified().contentHash(), file.verified().sizeBytes(), file.verified().mimeType(), file.scanStatus(),
            file.objectStatus(), clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
