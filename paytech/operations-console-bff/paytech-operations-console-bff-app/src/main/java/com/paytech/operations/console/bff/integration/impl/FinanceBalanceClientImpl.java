package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;

import org.springframework.stereotype.Component;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.operations.console.bff.integration.FinanceBalanceClient;
import com.paytech.operations.console.bff.service.model.FinanceBalanceModels.ClientBalanceResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBalanceModels.PartnerBalanceResponseBody;

import lombok.AllArgsConstructor;

/**
 * account 余额投影的 HTTP 转发：每个入口只调用一次 account，不重试
 * <li>回执的 Owner、entityNo 与币种必须与请求一致；无账簿时 account 以 BalanceQueryErrorCode 返回 400。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceBalanceClientImpl implements FinanceBalanceClient {
    private final BalanceQueryApi api;

    /** Client 分类余额。 */
    @Override
    public ClientBalanceResponseBody queryClientBalance(String clientId, String entityNo, String currency) {
        var balance = convert(api.queryClientBalance(clientId, entityNo, currency), ClientBalanceResponseBody.class);
        requireSame(clientId, balance.getClientId());
        requireSame(entityNo, balance.getEntityNo());
        requireSame(currency, balance.getCurrency());
        return balance;
    }

    /** Local Partner 分类余额。 */
    @Override
    public PartnerBalanceResponseBody queryPartnerBalance(String partnerId, String entityNo, String currency) {
        var balance = convert(api.queryPartnerBalance(partnerId, entityNo, currency), PartnerBalanceResponseBody.class);
        requireSame(partnerId, balance.getPartnerId());
        requireSame(entityNo, balance.getEntityNo());
        requireSame(currency, balance.getCurrency());
        return balance;
    }
}
