package com.paytech.payout.service.processor;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BalanceExplanation;
import com.paytech.payout.domain.OwnerBalancePosition;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.integration.OwnerBalanceClient;
import com.paytech.payout.service.processor.request.ExplainBalanceRequest;

import lombok.AllArgsConstructor;

/**
 * Owner 余额解释（P12，R6、B9、AC-04、AC-08）：account 余额列表 + 本库收款账户 → 各 OE × 币种的阻断原因
 * <li>只读、无事务、不缓存；account 调用在事务外（本用例无事务）。阻断判定按当前时刻实时计算冷却。</li>
 */
@Component
@AllArgsConstructor
public class ExplainBalanceProcessor extends AbstractProcessor<ExplainBalanceRequest, BalanceExplanation> {
    private final Clock clock;
    private final OwnerBalanceClient ownerBalanceClient;
    private final BeneficiaryAccountRepository beneficiaryAccountRepository;

    /** 查询余额与账户并判定。 */
    @Override
    protected BalanceExplanation processRequest(ExplainBalanceRequest request) {
        List<OwnerBalancePosition> positions = ownerBalanceClient.listPositions(request.getOwnerType(), request.getOwnerId());
        List<BeneficiaryAccount> accounts = beneficiaryAccountRepository.listByOwner(request.getOwnerType(),
            request.getOwnerId(), null, null);
        return BalanceExplanation.of(positions, accounts, clock.instant());
    }
}
