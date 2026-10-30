package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryCloseReason;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.processor.request.BeneficiaryAccountCommandRequest;

/**
 * 关闭收款账户（P5，B2、B5）：PENDING_REVIEW / VERIFIED / SUSPENDED → CLOSED（终态），清除默认并通知 Owner
 * <li>Client 成员只能关闭自己 Owner 的账户（归属不符按不存在处理）；关闭后要用同账号须新建账户。</li>
 */
@Component
public class CloseBeneficiaryAccountProcessor
    extends AbstractBeneficiaryCommandProcessor<BeneficiaryAccountCommandRequest, BeneficiaryAccountView> {
    private final BeneficiaryAccountService beneficiaryAccountService;

    /** 以时间源、仓储与本地服务构造。 */
    public CloseBeneficiaryAccountProcessor(Clock clock, BeneficiaryAccountRepository beneficiaryAccountRepository,
        BeneficiaryAccountService beneficiaryAccountService) {
        super(clock, beneficiaryAccountRepository);
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /** 目标账户。 */
    @Override
    protected Long accountId(BeneficiaryAccountCommandRequest request) {
        return request.getBeneficiaryAccountId();
    }

    /** 关闭原因必须属于关闭目录；Client 成员必须给出 Owner。 */
    @Override
    protected void validate(BeneficiaryAccountCommandRequest request) {
        requireReason(request.getReasonCode(), BeneficiaryCloseReason.class);
        requireOwnerForClientMember(request.getActorType(), request.getOwnerType());
    }

    /** 校验并关闭。 */
    @Override
    protected BeneficiaryAccountView processWithinLock(BeneficiaryAccountCommandRequest request) {
        // 业务验证
        BeneficiaryAccount account = loadAccount(request.getBeneficiaryAccountId());
        checkOwner(account, request.getOwnerType(), request.getOwnerId());
        if (!account.statusIn(BeneficiaryAccountStatus.PENDING_REVIEW, BeneficiaryAccountStatus.VERIFIED,
            BeneficiaryAccountStatus.SUSPENDED)) {
            throw statusConflict(account, "已拒绝或已关闭的账户不能关闭");
        }
        checkVersion(account, request.getExpectedVersion());

        // 本地保存
        beneficiaryAccountService.close(new BeneficiaryCommandContext(account, request.getActorType(), request.getActorId(),
            request.getReasonCode(), request.getSourceKey()));
        return view(account);
    }
}
