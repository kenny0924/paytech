package com.paytech.payout.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountEventRepository;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.service.processor.request.GetBeneficiaryAccountRequest;

import lombok.AllArgsConstructor;

/**
 * 查询收款账户详情（P3）：账户 + 事件时间线，运营视图另组合共用提示
 * <li>给出 Owner 时账户必须属于它，否则与不存在一样返回 404，不泄露其他 Owner 的账户。</li>
 */
@Component
@AllArgsConstructor
public class GetBeneficiaryAccountProcessor extends AbstractProcessor<GetBeneficiaryAccountRequest, BeneficiaryAccountView> {
    private final Clock clock;
    private final BeneficiaryAccountRepository beneficiaryAccountRepository;
    private final BeneficiaryAccountEventRepository beneficiaryAccountEventRepository;

    /** 读取并组合视图。 */
    @Override
    protected BeneficiaryAccountView processRequest(GetBeneficiaryAccountRequest request) {
        BeneficiaryAccount account = beneficiaryAccountRepository.findById(request.getBeneficiaryAccountId());
        boolean visible = account != null
            && (request.getOwnerType() == null || account.ownedBy(request.getOwnerType(), request.getOwnerId()));
        if (!visible) {
            throw new FailException(PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND, "收款账户不存在: "
                + request.getBeneficiaryAccountId());
        }
        return new BeneficiaryAccountView(account, account.usableAt(clock.instant()),
            request.isOperationsViewer() ? beneficiaryAccountRepository.listSharedAccounts(account,
                BeneficiaryAccountView.SHARED_ACCOUNT_LIMIT) : null,
            beneficiaryAccountEventRepository.listByAccount(account.getId()));
    }
}
