package com.paytech.ledger.integration.billing.impl;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.EconomicFactProgress;
import com.paytech.ledger.integration.billing.EconomicFactProgressClient;

import lombok.AllArgsConstructor;

/** 只适配 billing-api pending DTO（第 11a 步合同以账簿主体三元组代替 bookId）；HTTP 与错误解码交给 Foundation 公共客户端。 */
@Component
@AllArgsConstructor
public class EconomicFactProgressClientImpl implements EconomicFactProgressClient {
    private final EconomicFactCommandApi economicFactApi;

    /** 未发布计数必须存在。 */
    @Override
    public EconomicFactProgress getProgress(LedgerBook book) {
        PendingEconomicFactsResponseBody body = economicFactApi.getPendingEconomicFacts(book.getEntityType().getCode(),
            book.getEntityId(), book.getCurrency());
        if (body == null || body.getPendingCount() == null) {
            throw new ErrorException("billing pending 响应无效: 账簿 " + book.getId());
        }
        Long maxPublished = body.getMaxPublishedFactId() == null ? null : Long.valueOf(body.getMaxPublishedFactId());
        return new EconomicFactProgress(Long.parseLong(body.getPendingCount()), maxPublished);
    }
}
