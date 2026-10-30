package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiarySuspendReason;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.processor.request.BeneficiaryAccountCommandRequest;

/** 运营停用收款账户（P6，B2、B5）：VERIFIED → SUSPENDED，记原因、清除默认并通知 Owner。 */
@Component
public class SuspendBeneficiaryAccountProcessor
    extends AbstractBeneficiaryCommandProcessor<BeneficiaryAccountCommandRequest, BeneficiaryAccountView> {
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储与本地服务构造。 */
    public SuspendBeneficiaryAccountProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(BeneficiaryAccountCommandRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 只有运营可以停用；停用原因必须属于停用目录。 */
    @Override
    protected void validate(BeneficiaryAccountCommandRequest request) {
        if (request.getActorType() != BeneficiaryActorType.OPERATIONS) {
            throw new BizCheckFailException("只有运营可以停用收款账户: " + request.getBeneficiaryAccountId());
        }
        requireReason(request.getReasonCode(), BeneficiarySuspendReason.class);
    }

    /** 校验并停用。 */
    @Override
    protected BeneficiaryAccountView processWithinLock(BeneficiaryAccountCommandRequest request) {
        // 业务验证
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());
        checkOwner(account, request.getOwnerType(), request.getOwnerId());
        if (account.getStatus() != BeneficiaryAccountStatus.VERIFIED) {
            throw statusConflict(account, "只有核验通过的账户可以停用");
        }
        checkVersion(account, request.getExpectedVersion());

        // 本地保存
        beneficiaryAccountService.suspend(new BeneficiaryCommandContext(account, BeneficiaryActorType.OPERATIONS,
            request.getActorId(), request.getReasonCode(), request.getSourceKey()));
        return view(account);
    }
}
