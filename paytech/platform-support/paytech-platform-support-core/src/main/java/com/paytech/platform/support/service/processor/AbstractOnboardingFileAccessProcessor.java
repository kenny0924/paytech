package com.paytech.platform.support.service.processor;

import java.util.Objects;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient.Target;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Owned;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.service.processor.request.OnboardingFileAccessRequest;

/**
 * 客户读取或下载本人入驻文件的公共授权步骤
 * <li>先在只读快照中验证真实会话并读取原文件，再在事务外向原 Owner（CCT）确认申请主体仍可读。</li>
 * <li>网络等待后在新的只读快照中重新核对会话、原上传、原 Tenant 及完整 IAM，任何不符都按不存在处理。</li>
 * <li>不写业务数据，不需要资源锁。</li>
 *
 * @param <Response> 具体入口的内部结果
 */
abstract class AbstractOnboardingFileAccessProcessor<Response>
    extends AbstractProcessor<OnboardingFileAccessRequest, Response> {

    private final CctFileTargetClient targets;

    private final FileLifecycleService files;

    private final ClientOnboardingAccessService access;

    private final CustomerAuthenticationService authentication;

    private final OperationsTransactionExecutor transactions;

    /** 注入授权步骤的共同依赖。 */
    AbstractOnboardingFileAccessProcessor(CctFileTargetClient targets, FileLifecycleService files,
        ClientOnboardingAccessService access, CustomerAuthenticationService authentication,
        OperationsTransactionExecutor transactions) {
        this.targets = targets;
        this.files = files;
        this.access = access;
        this.authentication = authentication;
        this.transactions = transactions;
    }

    /**
     * 两次只读快照之间向 CCT 确认原申请主体，返回第二次快照中的当前文件事实。
     *
     * @return 当前账号拥有且 IAM 仍允许读取的文件
     */
    Owned authorized(OnboardingFileAccessRequest request) {
        Owned original = transactions.query(() -> readOwned(request));
        Target target = targets.read(request.getAccountId(), original.applicationId(), original.subjectPartyId());
        return transactions.query(() -> recheckOwned(request, original, target));
    }

    /** 事务内：校验会话后读取当前账号上传的原文件。 */
    Owned readOwned(OnboardingFileAccessRequest request) {
        authentication.context(request.getAccountId(), request.getSessionId());
        return files.read(request.getFileId(), request.getAccountId());
    }

    /** 事务内：重新校验会话，确认原申请、主体、上传与 Tenant 未变，再核对 IAM 读取权限。 */
    Owned recheckOwned(OnboardingFileAccessRequest request, Owned original, Target target) {
        Owned current = readOwned(request);
        if (!sameOrigin(original, current)) {
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        }
        access.readFile(request.getAccountId(), target.clientId(), current.upload().tenantId());
        return current;
    }

    /** 原申请、主体、上传与 Tenant 四项均一致。 */
    boolean sameOrigin(Owned original, Owned current) {
        return current.applicationId() == original.applicationId()
            && current.subjectPartyId() == original.subjectPartyId()
            && current.upload().uploadId() == original.upload().uploadId()
            && Objects.equals(current.upload().tenantId(), original.upload().tenantId());
    }

    /** 扫描通过与对象有效必须同时成立。 */
    boolean downloadable(Owned owned) {
        return owned.file().scanStatus() == FileScanStatus.AVAILABLE
            && owned.file().objectStatus() == FileObjectStatus.ACTIVE;
    }

    /** 当前不可下载不会触发重扫或恢复存储。 */
    void requireDownloadable(Owned owned) {
        if (!downloadable(owned)) {
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        }
    }
}
