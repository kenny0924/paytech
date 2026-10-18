package com.paytech.ledger.integration.account.impl;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.account.api.BalanceBookApi;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.OwnerBalanceCountResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.domain.BookBalanceConsistency;
import com.paytech.ledger.integration.account.BalanceConsistencyClient;

import lombok.AllArgsConstructor;

/** 只适配 account-api consistency DTO；HTTP、MDC 与错误解码交给 Foundation 公共客户端。 */
@Component
@AllArgsConstructor
public class BalanceConsistencyClientImpl implements BalanceConsistencyClient {
    /** 关账步骤 3 核对的渠道与银行账户核算对象类型。 */
    static final Set<String> TREASURY_OWNERS = Set.of(OwnerType.CHANNEL_ACCOUNT.getCode(), OwnerType.CASH_ACCOUNT.getCode());

    private final BalanceBookApi balanceBookApi;

    /** 响应必须回显请求的账簿，并列出非零账户统计。 */
    @Override
    public BookBalanceConsistency getConsistency(long ledgerBookId) {
        BalanceConsistencyResponseBody body = balanceBookApi.getBalanceConsistency(Long.toString(ledgerBookId));
        if (body == null || !Long.toString(ledgerBookId).equals(body.getLedgerBookId())
            || body.getNonZeroAccountCounts() == null) {
            throw new ErrorException("account consistency 响应无效: 账簿 " + ledgerBookId);
        }
        long treasury = 0;
        for (OwnerBalanceCountResponseBody count : body.getNonZeroAccountCounts()) {
            if (TREASURY_OWNERS.contains(count.getOwnerType())) {
                treasury += Long.parseLong(count.getCount());
            }
        }
        Long maxApplied = body.getMaxAppliedFactId() == null ? null : Long.valueOf(body.getMaxAppliedFactId());
        return new BookBalanceConsistency(ledgerBookId, maxApplied, treasury);
    }
}
