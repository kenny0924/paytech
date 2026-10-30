package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.processor.request.BeneficiaryAccountCommandRequest;

/**
 * 设为默认账户（P4，B5）：只有 VERIFIED 可设；同 Owner、同币种的原默认在同一事务清除
 * <li>已是默认时原样返回；单默认由 Owner 锁内"先清旧、再设新"保证。</li>
 */
@Component
public class SetDefaultBeneficiaryAccountProcessor
    extends AbstractBeneficiaryCommandProcessor<BeneficiaryAccountCommandRequest, BeneficiaryAccountView> {
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储与本地服务构造。 */
    public SetDefaultBeneficiaryAccountProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(BeneficiaryAccountCommandRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 设默认不接受原因代码；Client 成员必须给出 Owner。 */
    @Override
    protected void validate(BeneficiaryAccountCommandRequest request) {
        rejectReason(request.getReasonCode());
        requireOwnerForClientMember(request.getActorType(), request.getOwnerType());
    }

    /** 校验并切换默认。 */
    @Override
    protected BeneficiaryAccountView processWithinLock(BeneficiaryAccountCommandRequest request) {
        // 业务验证
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());
        checkOwner(account, request.getOwnerType(), request.getOwnerId());
        if (account.getStatus() != BeneficiaryAccountStatus.VERIFIED) {
            throw statusConflict(account, "只有核验通过的账户可以设为默认");
        }
        if (Boolean.TRUE.equals(account.getIsDefault())) {
            return view(account);
        }
        checkVersion(account, request.getExpectedVersion());
        BeneficiaryAccount previous = beneficiaryAccountRepository.findDefault(account.getOwnerType(), account.getOwnerId(),
            account.getCurrency());

        // 本地保存
        beneficiaryAccountService.setDefault(new BeneficiaryCommandContext(account, request.getActorType(),
            request.getActorId(), null, request.getSourceKey()), previous);
        return view(account);
    }
}
