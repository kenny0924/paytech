package com.paytech.client.master.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.SubmitClientApplicationContext;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.service.ClientSubmissionService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.SubmitClientApplicationRequest;
import com.paytech.client.master.service.processor.response.SubmitClientApplicationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/**
 * C14 正式提交申请。
 * <li>与草稿、人员、材料写入共用 client-onboarding:application:{id} 锁，提交期间不能再保存。</li>
 * <li>已是 SUBMITTED/UNDER_REVIEW 时按当前状态返回原提交，不重新检查；不读取 Idempotency-Key。</li>
 * <li>Platform 会话、Risk 材料要求和 Platform 文件核验都在事务外完成；本地事务只保存状态、身份和审核登记任务。</li>
 * <li>不完整返回 SUBMISSION_INCOMPLETE，逐项问题由提交核对查询提供；证件号被占用返回 IDENTITY_CONFLICT。</li>
 */
public class SubmitClientApplicationProcessor
    extends AbstractLockProcessor<SubmitClientApplicationRequest, SubmitClientApplicationResponse> {
    private final Clock clock;
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDraftRepository drafts;
    private final ClientSubmissionService submissions;
    private final ClientDraftAccountClient accounts;
    private final ClientSubmissionPreparer preparer;
    private final ClientApplicationRepository applications;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public SubmitClientApplicationProcessor(Clock clock, Duration lockWait, Duration lockLease, ClientDraftRepository drafts,
        ClientSubmissionService submissions, ClientDraftAccountClient accounts, ClientSubmissionPreparer preparer,
        ClientApplicationRepository applications) {
        this.clock = clock;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.drafts = drafts;
        this.submissions = submissions;
        this.accounts = accounts;
        this.preparer = preparer;
        this.applications = applications;
    }

    /** 与 @ResourceLock(prefix="client-onboarding:application") 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(SubmitClientApplicationRequest request) {
        return LockCommand.tryLocks(List.of("client-onboarding:application:" + request.getApplicationId()), lockWait, lockLease);
    }

    @Override
    protected SubmitClientApplicationResponse processWithinLock(SubmitClientApplicationRequest request) {
        // 归属与当前提交
        accounts.authorize(request.getAccountId(), request.getSessionId());
        ClientDraftIdentity identity = ClientDraftPolicy.requireValid(
            drafts.identity(request.getApplicationId(), request.getAccountId(), false));
        ClientApplicationSubmission application = applications.findByApplication(request.getApplicationId(), request.getAccountId());
        if (application.getStatus() == ClientApplicationStatus.SUBMITTED
            || application.getStatus() == ClientApplicationStatus.UNDER_REVIEW) {
            return response(application, false);
        }
        if (!ClientDraftPolicy.isEditable(identity)) {
            throw new FailException(APPLICATION_NOT_EDITABLE);
        }

        // 构建上下文：事务外读取 Owner 事实并完成检查
        SubmitClientApplicationContext context = buildContext(request, identity, application);

        // 保存上下文
        accounts.authorize(request.getAccountId(), request.getSessionId());
        try {
            submissions.submit(context);
        } catch (DuplicateKeyException collision) {
            String reason = collision.getMostSpecificCause().getMessage();
            if (reason != null && reason.contains("uk_party_identity_norm")) {
                throw new FailException(IDENTITY_CONFLICT);
            }
            throw collision;
        }

        // 响应处理
        return response(application, true);
    }

    /** 检查通过后一次构造完整上下文并生成新提交编号。 */
    SubmitClientApplicationContext buildContext(SubmitClientApplicationRequest request, ClientDraftIdentity identity,
        ClientApplicationSubmission application) {
        var preparation = preparer.prepare(request.getAccountId(), identity);
        if (preparation.isIdentityConflict()) {
            throw new FailException(IDENTITY_CONFLICT);
        }
        if (!preparation.ready()) {
            throw new FailException(SUBMISSION_INCOMPLETE);
        }
        return new SubmitClientApplicationContext(identity, application, preparation.getSubjects(), preparation.getRelations(),
            preparation.getDocuments(), UUID.randomUUID().toString(), clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 从提交坐标构造响应。 */
    SubmitClientApplicationResponse response(ClientApplicationSubmission application, boolean created) {
        return new SubmitClientApplicationResponse(application.getApplicationId(), application.getCurrentSubmissionId(),
            application.getStatus().getCode(), created);
    }
}
