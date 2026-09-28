package com.paytech.finance.ext.rest.balance;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.domain.model.ClientBalance;
import com.paytech.finance.accounting.domain.model.PartnerBalance;
import com.paytech.finance.accounting.domainservice.service.BalanceQueryService;
import com.paytech.finance.api.balance.BalanceModels.ClientBalanceResponseBody;
import com.paytech.finance.api.balance.BalanceModels.PartnerBalanceResponseBody;
import com.paytech.finance.api.balance.BalanceQueryApi;

import lombok.RequiredArgsConstructor;

/** Client 与 Local Partner 分类余额的 HTTP 协议适配。 */
@RestController
@RequiredArgsConstructor
public class BalanceQueryController implements BalanceQueryApi {
    private final BalanceQueryService service;

    @Override
    public ClientBalanceResponseBody queryClientBalance(String clientId, String operatingEntityId, String currency) {
        return response(service.clientBalance(clientId, operatingEntityId, currency));
    }

    @Override
    public PartnerBalanceResponseBody queryPartnerBalance(String partnerId, String operatingEntityId, String currency) {
        return response(service.partnerBalance(partnerId, operatingEntityId, currency));
    }

    private ClientBalanceResponseBody response(ClientBalance balance) {
        return new ClientBalanceResponseBody(Long.toString(balance.clientId()),
            Long.toString(balance.operatingEntityId()), balance.currency(), balance.pendingAmount(),
            balance.availableAmount(), balance.lockedAmount(), balance.securityDepositAmount(),
            balance.recoveryReceivableAmount());
    }

    private PartnerBalanceResponseBody response(PartnerBalance balance) {
        return new PartnerBalanceResponseBody(Long.toString(balance.partnerId()),
            Long.toString(balance.operatingEntityId()), balance.currency(), balance.pendingAmount(),
            balance.availableAmount(), balance.lockedAmount(), balance.recoveryReceivableAmount());
    }
}
