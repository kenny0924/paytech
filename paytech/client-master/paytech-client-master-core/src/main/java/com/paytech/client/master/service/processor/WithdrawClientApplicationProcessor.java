package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.APPLICATION_NOT_FOUND;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.api.onboarding.response.WithdrawalResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.service.ClientApplicationCommandService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.WithdrawClientApplicationRequest;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-8 Client 撤回申请：DRAFT / RETURNED → WITHDRAWN，释放账号名额
 * <li>与草稿、人员、材料、声明及提交共用 client-onboarding:application:{id} 锁；条件更新保护状态。</li>
 * <li>已立案（RETURNED 有审核任务）时同一事务登记 CLIENT_REVIEW_WITHDRAWAL，提交后由补偿任务调用 compliance I03。</li>
 * <li>已是 WITHDRAWN 返回原结果；SUBMITTED、UNDER_REVIEW 及其他终态拒绝为 APPLICATION_NOT_WITHDRAWABLE。</li>
 */
@AllArgsConstructor
public class WithdrawClientApplicationProcessor
    extends AbstractLockProcessor<WithdrawClientApplicationRequest, WithdrawalResponseBody> {
    private final Clock clock;
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDraftAccountClient accounts;
    private final ClientApplicationRepository applications;
    private final ClientApplicationCommandService commands;

    /** 与草稿写入同一申请锁。 */
    @Override
    protected LockCommand lockCommand(WithdrawClientApplicationRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait, lockLease);
    }

    /** 归属与状态 → 本地撤回（含补偿登记）→ 响应。 */
    @Override
    protected WithdrawalResponseBody processWithinLock(WithdrawClientApplicationRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        ClientApplicationSubmission application = applications.findByApplication(request.getApplicationId(), request.getAccountId());
        if (application == null) {
            throw new FailException(APPLICATION_NOT_FOUND);
        }
        if (application.getStatus() == ClientApplicationStatus.WITHDRAWN) {
            return response(application);
        }
        if (application.getStatus() != ClientApplicationStatus.DRAFT && application.getStatus() != ClientApplicationStatus.RETURNED) {
            throw new FailException(OnboardingErrorCode.APPLICATION_NOT_WITHDRAWABLE);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!commands.withdraw(application, now)) {
            throw new ConcurrentRequestException("申请状态已变化，请刷新后重试");
        }
        return response(application);
    }

    /** 撤回结果。 */
    WithdrawalResponseBody response(ClientApplicationSubmission application) {
        var body = new WithdrawalResponseBody();
        body.setApplicationId(Long.toString(application.getApplicationId()));
        body.setStatus(application.getStatus().getCode());
        body.setWithdrawnAt(new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(application.getUpdateTime()));
        return body;
    }
}
