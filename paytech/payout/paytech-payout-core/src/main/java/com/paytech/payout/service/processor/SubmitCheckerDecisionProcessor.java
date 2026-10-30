package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryRejectReason;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.configuration.PayoutBeneficiaryProperties;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.processor.request.CheckerDecisionRequest;

/**
 * 作出 Checker 复核决定（P9，B3、B4）：批准 → VERIFIED 并冷却，拒绝 → REJECTED；均通知 Owner
 * <li>四眼：Checker 不能是 Maker，也不能是运营代提交人（403 FOUR_EYES_REQUIRED）。</li>
 * <li>批准要求 Maker 意见为 APPROVE；Maker 建议拒绝时 Checker 只能拒绝。</li>
 * <li>冷却结束时间 = 决定时间 + payout.beneficiary.cooling，到期后按时间实时判定可用，不需要任务。</li>
 */
@Component
public class SubmitCheckerDecisionProcessor
    extends AbstractBeneficiaryCommandProcessor<CheckerDecisionRequest, BeneficiaryAccountView> {
    private final PayoutBeneficiaryProperties beneficiaryProperties;
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储、冷却配置与本地服务构造。 */
    public SubmitCheckerDecisionProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        PayoutBeneficiaryProperties beneficiaryProperties, BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryProperties = beneficiaryProperties;
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(CheckerDecisionRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 校验并记录决定。 */
    @Override
    protected BeneficiaryAccountView processWithinLock(CheckerDecisionRequest request) {
        // 业务验证
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());
        checkReviewable(account, request);
        checkFourEyes(account, request.getActorId());
        checkContentHash(account, request.getContentHash());
        checkVersion(account, request.getExpectedVersion());
        String reason = reason(account, request);

        // 本地保存
        beneficiaryAccountService.recordCheckerDecision(new BeneficiaryCommandContext(account,
            BeneficiaryActorType.OPERATIONS, request.getActorId(), reason, request.getSourceKey()), request.getDecision(),
            reasonNote(account, request), beneficiaryProperties.getCooling());
        return view(account);
    }

    /** 只有待核验且已有 Maker 意见的账户可以复核。 */
    static void checkReviewable(BeneficiaryAccount account, CheckerDecisionRequest request) {
        if (account.getStatus() != BeneficiaryAccountStatus.PENDING_REVIEW) {
            throw statusConflict(account, "只有待核验的账户可以复核");
        }
        if (account.getReviewMakerId() == null) {
            throw statusConflict(account, "该账户尚无 Maker 意见");
        }
    }

    /** Checker 与 Maker、运营代提交人必须是不同的 Platform 账号。 */
    static void checkFourEyes(BeneficiaryAccount account, Long checkerId) {
        boolean sameAsMaker = checkerId.equals(account.getReviewMakerId());
        boolean sameAsSubmitter = account.getSubmittedByType() == BeneficiaryActorType.OPERATIONS
            && checkerId.equals(account.getSubmittedBy());
        if (sameAsMaker || sameAsSubmitter) {
            throw new FailException(PayoutErrorCode.FOUR_EYES_REQUIRED, "需由另一人复核: 收款账户 " + account.getId()
                + ", 操作人 " + checkerId);
        }
    }

    /** 拒绝原因必填；批准要求 Maker 建议通过且不接受原因。 */
    static String reason(BeneficiaryAccount account, CheckerDecisionRequest request) {
        if (request.getDecision() == CheckerDecision.REJECTED) {
            return requireReason(request.getReasonCode(), BeneficiaryRejectReason.class);
        }
        if (account.getReviewMakerDecision() != MakerReviewDecision.APPROVE) {
            throw statusConflict(account, "Maker 建议拒绝，不能批准");
        }
        rejectReason(request.getReasonCode());
        rejectClientNote(request.getClientNote());
        return null;
    }

    /** 拒绝时给 Owner 的说明：Checker 填写的优先，未填写时沿用 Maker 建议拒绝时的说明；批准时为空。 */
    static String reasonNote(BeneficiaryAccount account, CheckerDecisionRequest request) {
        if (request.getDecision() != CheckerDecision.REJECTED) {
            return null;
        }
        String checkerNote = normalizeNote(request.getClientNote());
        return checkerNote != null ? checkerNote : account.getReasonNote();
    }
}
