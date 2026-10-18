package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.domain.model.PartnerBalance;
import com.paytech.account.domainservice.service.BalanceQueryService;
import com.paytech.account.integration.ledger.LedgerBookClient;
import com.paytech.account.service.processor.request.OwnerBalanceQuery;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 查询 Local Partner 在经营主体币种账簿下的分类余额（FIN-I02）
 * <li>先经 ledger-api 确认经营主体币种账簿存在（ledger 不可用时失败关闭，design/04 §6），再读本服务余额；未建立的余额按零返回。</li>
 */
@Component
@AllArgsConstructor
public class QueryPartnerBalanceProcessor extends AbstractProcessor<OwnerBalanceQuery, PartnerBalance> {
    private final LedgerBookClient ledgerBookClient;
    private final BalanceQueryService balanceQueryService;

    @Override
    protected void validate(OwnerBalanceQuery request) {
        Money.zero(request.getCurrency());
    }

    @Override
    protected PartnerBalance processRequest(OwnerBalanceQuery request) {
        if (ledgerBookClient.findOperatingEntityBook(request.getEntityNo(), request.getCurrency()) == null) {
            throw new BizCheckFailException("未找到经营主体币种账簿: " + request.getEntityNo() + "/" + request.getCurrency());
        }
        return balanceQueryService.partnerBalance(request.getOwnerId(), request.getEntityNo(), request.getCurrency());
    }
}
