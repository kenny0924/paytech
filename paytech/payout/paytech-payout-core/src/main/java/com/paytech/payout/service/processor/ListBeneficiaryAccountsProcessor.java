package com.paytech.payout.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.service.processor.request.ListBeneficiaryAccountsRequest;

import lombok.AllArgsConstructor;

/**
 * 查询 Owner 收款账户列表（P2）：账户 + 实时可用判定，运营视图另组合跨 Owner 共用提示（B6）
 * <li>普通读取，不加锁、不开事务。</li>
 */
@Component
@AllArgsConstructor
public class ListBeneficiaryAccountsProcessor
    extends AbstractProcessor<ListBeneficiaryAccountsRequest, List<BeneficiaryAccountView>> {
    private final Clock clock;
    private final BeneficiaryAccountRepository beneficiaryAccountRepository;

    /** 读取并组合视图。 */
    @Override
    protected List<BeneficiaryAccountView> processRequest(ListBeneficiaryAccountsRequest request) {
        Instant now = clock.instant();
        List<BeneficiaryAccount> accounts = beneficiaryAccountRepository.listByOwner(request.getOwnerType(),
            request.getOwnerId(), request.getStatus(), request.getCurrency());
        return accounts.stream().map(account -> new BeneficiaryAccountView(account, account.usableAt(now),
            request.isOperationsViewer() ? beneficiaryAccountRepository.listSharedAccounts(account,
                BeneficiaryAccountView.SHARED_ACCOUNT_LIMIT) : null, null))
            .toList();
    }
}
