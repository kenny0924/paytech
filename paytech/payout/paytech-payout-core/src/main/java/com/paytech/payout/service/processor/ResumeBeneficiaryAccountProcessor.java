package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.processor.request.BeneficiaryAccountCommandRequest;

/**
 * 运营恢复收款账户（P7，B2）：SUSPENDED → PENDING_REVIEW，清空 Maker / Checker 结果与冷却，重新核验
 * <li>冻结内容与摘要不变；不发通知（重新核验通过时再通知）。</li>
 */
@Component
public class ResumeBeneficiaryAccountProcessor
    extends AbstractBeneficiaryCommandProcessor<BeneficiaryAccountCommandRequest, BeneficiaryAccountView> {
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储与本地服务构造。 */
    public ResumeBeneficiaryAccountProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(BeneficiaryAccountCommandRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 只有运营可以恢复；恢复不接受原因代码。 */
    @Override
    protected void validate(BeneficiaryAccountCommandRequest request) {
        if (request.getActorType() != BeneficiaryActorType.OPERATIONS) {
            throw new BizCheckFailException("只有运营可以恢复收款账户: " + request.getBeneficiaryAccountId());
        }
        rejectReason(request.getReasonCode());
    }

    /** 校验并恢复。 */
    @Override
    protected BeneficiaryAccountView processWithinLock(BeneficiaryAccountCommandRequest request) {
        // 业务验证
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());
        checkOwner(account, request.getOwnerType(), request.getOwnerId());
        if (account.getStatus() != BeneficiaryAccountStatus.SUSPENDED) {
            throw statusConflict(account, "只有已停用的账户可以恢复");
        }
        checkVersion(account, request.getExpectedVersion());

        // 本地保存
        beneficiaryAccountService.resume(new BeneficiaryCommandContext(account, BeneficiaryActorType.OPERATIONS,
            request.getActorId(), null, request.getSourceKey()));
        return view(account);
    }
}
