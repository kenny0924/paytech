package com.paytech.finance.ext.rest.balance;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.domain.model.ClientBalance;
import com.paytech.finance.accounting.domain.model.PartnerBalance;
import com.paytech.finance.accounting.domainservice.service.BalanceQueryService;
import com.paytech.finance.api.balance.BalanceQueryApi;
import com.paytech.finance.api.balance.response.ClientBalanceResponseBody;
import com.paytech.finance.api.balance.response.PartnerBalanceResponseBody;

import lombok.AllArgsConstructor;

/** Client 与 Local Partner 分类余额的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class BalanceQueryController implements BalanceQueryApi {
    private final BalanceQueryService service;

    /** 查询 Client 分类余额。 */
    @Override
    public ClientBalanceResponseBody queryClientBalance(String clientId, String entityNo, String currency) {
        ClientBalance balance = service.clientBalance(clientId, entityNo, currency);
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
        PartnerBalance balance = service.partnerBalance(partnerId, entityNo, currency);
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
