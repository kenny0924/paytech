package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountDetails;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.integration.BeneficiaryVaultClient;
import com.paytech.payout.service.processor.request.RevealBeneficiaryAccountRequest;

/**
 * 运营查看收款账户原值（P10，B7、AC-07）：事务外 Vault Reveal（用途 PAYOUT_BENEFICIARY_REVIEW），记 REVEAL 事件后才返回
 * <li>只对 Operations BFF 开放（403 BENEFICIARY_REVEAL_FORBIDDEN）；运营内的 PAYOUT_BENEFICIARY_REVEAL 权限由 Gateway / BFF 判定。</li>
 * <li>REVEAL 事件与 Vault 侧访问审计互证；事件记录失败时不返回原值。原值不缓存、不落库、不写日志。</li>
 * <li>锁 Owner 以保证事件序号连续。</li>
 */
@Component
public class RevealBeneficiaryAccountProcessor
    extends AbstractBeneficiaryCommandProcessor<RevealBeneficiaryAccountRequest, BeneficiaryAccountDetails> {
    private final BeneficiaryVaultClient beneficiaryVaultClient;
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储、Vault 端口与本地服务构造。 */
    public RevealBeneficiaryAccountProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        BeneficiaryVaultClient beneficiaryVaultClient, BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryVaultClient = beneficiaryVaultClient;
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(RevealBeneficiaryAccountRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 只有 Operations BFF 可以查看原值。 */
    @Override
    protected void validate(RevealBeneficiaryAccountRequest request) {
        if (!request.isOperationsCaller()) {
            throw new FailException(PayoutErrorCode.BENEFICIARY_REVEAL_FORBIDDEN, "无权查看收款账户原值: "
                + request.getBeneficiaryAccountId());
        }
    }

    /** Reveal、记事件、返回原值。 */
    @Override
    protected BeneficiaryAccountDetails processWithinLock(RevealBeneficiaryAccountRequest request) {
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());

        // 事务外 Vault Reveal
        BeneficiaryAccountDetails details = BeneficiaryAccountDetails.fromVaultJson(
            beneficiaryVaultClient.revealDetails(account.getBeneficiaryVaultToken()));

        // 本地保存
        beneficiaryAccountService.recordReveal(new BeneficiaryCommandContext(account, BeneficiaryActorType.OPERATIONS,
            request.getActorId(), request.getReason().getCode(), request.getSourceKey()));
        return details;
    }
}
