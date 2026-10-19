package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceQueryErrorCode;
import com.paytech.account.domain.model.ClientBalance;
import com.paytech.account.domainservice.service.BalanceQueryService;
import com.paytech.account.service.processor.request.OwnerBalanceQuery;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 查询 Client 在经营主体与币种下的分类余额（FIN-I01）
 * <li>只读本库 Owner 账户，不调用 ledger：该 Owner 在此 OE 与币种下没有任何账户时 OWNER_ACCOUNT_NOT_FOUND，有账户但缺少某个桶时该金额为零。</li>
 */
@Component
@AllArgsConstructor
public class QueryClientBalanceProcessor extends AbstractProcessor<OwnerBalanceQuery, ClientBalance> {
    private final BalanceQueryService balanceQueryService;

    @Override
    protected void validate(OwnerBalanceQuery request) {
        Money.zero(request.getCurrency());
    }

    @Override
    protected ClientBalance processRequest(OwnerBalanceQuery request) {
        ClientBalance balance = balanceQueryService.clientBalance(request.getOwnerId(), request.getEntityNo(),
            request.getCurrency());
        if (balance == null) {
            throw new FailException(BalanceQueryErrorCode.OWNER_ACCOUNT_NOT_FOUND);
        }
        return balance;
    }
}
