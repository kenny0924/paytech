package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientClassification;
import com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.ClientClassificationContext;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationService;
import com.paytech.client.master.service.processor.request.ClientClassificationRequest;
import com.paytech.client.master.service.processor.response.ClientClassificationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * O04 确认分类：固定本任务分类，并在同一本地事务登记交付 Risk（I02）的补偿任务
 * <li>已为本任务确认时直接返回原确认结果，不重复登记。</li>
 * <li>交付 Risk 在提交后由 ClientClassificationHandler 完成，本请求不等待 Risk。</li>
 */
public class ConfirmClientClassificationProcessor
    extends AbstractLockProcessor<ClientClassificationRequest, ClientClassificationResponse> {
    private final Clock clock;
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientBusinessProfileRepository profiles;
    private final ClientClassificationService classifications;
    private final ClientApplicationRepository applications;
    private final ClientBusinessClassificationRepository rows;

    /** 锁等待与租约沿用 CCT 既有资源锁配置。 */
    public ConfirmClientClassificationProcessor(Clock clock, Duration lockWait, Duration lockLease,
        ClientBusinessProfileRepository profiles, ClientClassificationService classifications, ClientApplicationRepository applications,
        ClientBusinessClassificationRepository rows) {
        this.clock = clock;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.profiles = profiles;
        this.classifications = classifications;
        this.applications = applications;
        this.rows = rows;
    }

    /** 与 O03 共用的任务锁。 */
    @Override
    protected LockCommand lockCommand(ClientClassificationRequest request) {
        return LockCommand.tryLocks(List.of("client-onboarding:review-case:" + request.getCaseId()), lockWait, lockLease);
    }

    @Override
    protected ClientClassificationResponse processWithinLock(ClientClassificationRequest request) {
        // 当前任务与原确认
        ClientApplicationSubmission application = GetClientClassificationProcessor.currentApplication(applications, request.getCaseId());
        ClientClassificationProfile profile = GetClientClassificationProcessor.classificationProfile(profiles, application);
        if (profile.confirmedFor(request.getCaseId())) {
            return GetClientClassificationProcessor.response(request.getCaseId(), application, profile, rows);
        }
        if (application.getStatus() != ClientApplicationStatus.UNDER_REVIEW) {
            throw new FailException(APPLICATION_NOT_EDITABLE);
        }

        // 构建上下文
        ClientClassificationContext context = buildContext(request, application, profile);

        // 保存上下文
        classifications.confirm(context);

        // 响应处理
        return new ClientClassificationResponse(request.getCaseId(), application.getApplicationId(), profile,
            context.getClassifications());
    }

    /** 分类必须已绑定本任务且非空。 */
    ClientClassificationContext buildContext(ClientClassificationRequest request, ClientApplicationSubmission application,
        ClientClassificationProfile profile) {
        if (!Long.valueOf(request.getCaseId()).equals(profile.getClassificationCaseId())) {
            throw new FailException(CLASSIFICATION_INVALID);
        }
        List<ClientClassification> current = rows.findByProfile(profile.getBusinessProfileId());
        if (current.isEmpty()) {
            throw new FailException(CLASSIFICATION_INVALID);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return new ClientClassificationContext(request.getCaseId(), application, profile, current, request.getActorId(), now);
    }
}
