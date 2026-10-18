package com.paytech.account.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.account.domain.model.ClientBalance;
import com.paytech.account.domain.model.PartnerBalance;
import com.paytech.account.service.processor.QueryClientBalanceProcessor;
import com.paytech.account.service.processor.QueryPartnerBalanceProcessor;
import com.paytech.account.service.processor.request.OwnerBalanceQuery;

import lombok.AllArgsConstructor;

/** Client 与 Local Partner 分类余额的 HTTP 协议适配；账簿经 ledger-api 确认，委托 Processor。 */
@RestController
@AllArgsConstructor
public class BalanceQueryController implements BalanceQueryApi {
    private final QueryClientBalanceProcessor clientBalanceProcessor;
    private final QueryPartnerBalanceProcessor partnerBalanceProcessor;

    /** 查询 Client 分类余额。 */
    @Override
    public ClientBalanceResponseBody queryClientBalance(String clientId, String entityNo, String currency) {
        ClientBalance balance = clientBalanceProcessor.process(new OwnerBalanceQuery(clientId, entityNo, currency));
        ClientBalanceResponseBody body = new ClientBalanceResponseBody();
        body.setClientId(balance.getClientId());
        body.setEntityNo(balance.getEntityNo());
        body.setCurrency(balance.getCurrency());
        body.setPendingAmount(balance.getPendingAmount());
        body.setAvailableAmount(balance.getAvailableAmount());
        body.setLockedAmount(balance.getLockedAmount());
        body.setSecurityDepositAmount(balance.getSecurityDepositAmount());
        body.setRecoveryReceivableAmount(balance.getRecoveryReceivableAmount());
        return body;
    }

    /** 查询 Local Partner 分类余额。 */
    @Override
    public PartnerBalanceResponseBody queryPartnerBalance(String partnerId, String entityNo, String currency) {
        PartnerBalance balance = partnerBalanceProcessor.process(new OwnerBalanceQuery(partnerId, entityNo, currency));
        PartnerBalanceResponseBody body = new PartnerBalanceResponseBody();
        body.setPartnerId(balance.getPartnerId());
        body.setEntityNo(balance.getEntityNo());
        body.setCurrency(balance.getCurrency());
        body.setPendingAmount(balance.getPendingAmount());
        body.setAvailableAmount(balance.getAvailableAmount());
        body.setLockedAmount(balance.getLockedAmount());
        body.setRecoveryReceivableAmount(balance.getRecoveryReceivableAmount());
        return body;
    }
}
