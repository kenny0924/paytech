package com.paytech.account.app.ext.rest;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.account.domain.model.ClientBalance;
import com.paytech.account.domain.model.PartnerBalance;
import com.paytech.account.domainservice.service.BalanceQueryService;
import com.paytech.account.service.processor.QueryClientBalanceProcessor;
import com.paytech.account.service.processor.QueryPartnerBalanceProcessor;
import com.paytech.account.service.processor.request.OwnerBalanceQuery;

import lombok.AllArgsConstructor;

/**
 * Client 与 Local Partner 分类余额的 HTTP 协议适配；只读本库 Owner 账户
 * <li>单主体单币种查询委托 Processor；全部主体与币种的列表只做协议适配，直接委托余额查询 Domain Service。</li>
 */
@RestController
@AllArgsConstructor
public class BalanceQueryController implements BalanceQueryApi {
    private final BalanceQueryService balanceQueryService;
    private final QueryClientBalanceProcessor clientBalanceProcessor;
    private final QueryPartnerBalanceProcessor partnerBalanceProcessor;

    /** 查询 Client 分类余额。 */
    @Override
    public ClientBalanceResponseBody queryClientBalance(String clientId, String entityNo, String currency) {
        return clientBody(clientBalanceProcessor.process(new OwnerBalanceQuery(clientId, entityNo, currency)));
    }

    /** 查询 Local Partner 分类余额。 */
    @Override
    public PartnerBalanceResponseBody queryPartnerBalance(String partnerId, String entityNo, String currency) {
        return partnerBody(partnerBalanceProcessor.process(new OwnerBalanceQuery(partnerId, entityNo, currency)));
    }

    /** 列出 Client 全部经营主体与币种的分类余额。 */
    @Override
    public List<ClientBalanceResponseBody> listClientPositions(String clientId) {
        return balanceQueryService.clientPositions(clientId).stream().map(BalanceQueryController::clientBody).toList();
    }

    /** 列出 Local Partner 全部经营主体与币种的分类余额。 */
    @Override
    public List<PartnerBalanceResponseBody> listPartnerPositions(String partnerId) {
        return balanceQueryService.partnerPositions(partnerId).stream().map(BalanceQueryController::partnerBody)
            .toList();
    }

    /** Client 分类余额转响应。 */
    static ClientBalanceResponseBody clientBody(ClientBalance balance) {
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

    /** Local Partner 分类余额转响应。 */
    static PartnerBalanceResponseBody partnerBody(PartnerBalance balance) {
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
