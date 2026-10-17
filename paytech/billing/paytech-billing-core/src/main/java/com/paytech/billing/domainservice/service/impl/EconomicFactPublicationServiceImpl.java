package com.paytech.billing.domainservice.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.domain.EconomicFactContext;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.repository.EconomicFactLineRepository;
import com.paytech.billing.domainservice.repository.EconomicFactRepository;
import com.paytech.billing.domainservice.service.EconomicFactPublicationService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** EconomicFactPublicationService 实现：只访问 mor_billing；发布标记为单条条件更新，不需要显式事务。 */
@Service
@AllArgsConstructor
public class EconomicFactPublicationServiceImpl implements EconomicFactPublicationService {
    private final CurrencySpecRepository currencySpecRepository;
    private final EconomicFactRepository economicFactRepository;
    private final EconomicFactLineRepository economicFactLineRepository;

    /** 读取事实头、明细行与币种精度；不存在返回空。 */
    @Override
    public EconomicFactContext loadContext(long factId) {
        EconomicFact fact = economicFactRepository.findById(factId);
        if (fact == null) {
            return null;
        }
        CurrencySpec spec = currencySpecRepository.findByCurrency(fact.getCurrency());
        if (spec == null) {
            throw new ErrorException("经济事实币种缺少币种规则: " + fact.getCurrency());
        }
        return new EconomicFactContext(fact, economicFactLineRepository.findByEconomics(factId), null,
            spec.getMinorUnit());
    }

    /** 首次发布确认时写入 published_at。 */
    @Override
    public boolean markPublished(EconomicFactContext context, Instant now) {
        return economicFactRepository.markPublished(context.getFact(), now);
    }
}
