package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryRejectReason;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.processor.request.MakerReviewRequest;

/**
 * 出具 Maker 核验意见（P8，B3）：PENDING_REVIEW 且尚无意见时记录，状态不变
 * <li>每个账户（每轮核验）只允许一次 Maker 意见；审阅的摘要与版本必须与当前一致。</li>
 */
@Component
public class SubmitMakerReviewProcessor extends AbstractBeneficiaryCommandProcessor<MakerReviewRequest, BeneficiaryAccountView> {
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储与本地服务构造。 */
    public SubmitMakerReviewProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(MakerReviewRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 校验并记录意见。 */
    @Override
    protected BeneficiaryAccountView processWithinLock(MakerReviewRequest request) {
        // 业务验证
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());
        if (account.getStatus() != BeneficiaryAccountStatus.PENDING_REVIEW) {
            throw statusConflict(account, "只有待核验的账户可以出具 Maker 意见");
        }
        if (account.getReviewMakerId() != null) {
            throw statusConflict(account, "该账户已有 Maker 意见");
        }
        checkContentHash(account, request.getContentHash());
        checkVersion(account, request.getExpectedVersion());
        String reason = reason(request);

        // 本地保存
        beneficiaryAccountService.recordMakerReview(new BeneficiaryCommandContext(account, BeneficiaryActorType.OPERATIONS,
            request.getActorId(), reason, request.getSourceKey()), request.getDecision(), normalizeNote(request.getNote()),
            normalizeNote(request.getClientNote()));
        return view(account);
    }

    /** 建议拒绝时原因必填且属于拒绝目录；建议通过时不接受原因与给 Owner 的说明。 */
    static String reason(MakerReviewRequest request) {
        if (request.getDecision() == MakerReviewDecision.REJECT) {
            return requireReason(request.getReasonCode(), BeneficiaryRejectReason.class);
        }
        rejectReason(request.getReasonCode());
        rejectClientNote(request.getClientNote());
        return null;
    }
}
