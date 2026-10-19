package com.paytech.billing.domainservice.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domainservice.repository.BillingFactLineRepository;
import com.paytech.billing.domainservice.repository.BillingFactRefRepository;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.service.BillingFactPublicationService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BillingFactPublicationService 实现：只访问 mor_billing；发布标记为单条条件更新，不需要显式事务。 */
@Service
@AllArgsConstructor
public class BillingFactPublicationServiceImpl implements BillingFactPublicationService {
    private final BillingFactRepository billingFactRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final BillingFactRefRepository billingFactRefRepository;
    private final BillingFactLineRepository billingFactLineRepository;

    /** 读取事实头、引用、明细行与币种精度；不存在返回空。 */
    @Override
    public BillingFactContext loadContext(long factId) {
        BillingFact fact = billingFactRepository.findById(factId);
        if (fact == null) {
            return null;
        }
        CurrencySpec spec = currencySpecRepository.findByCurrency(fact.getCurrency());
        if (spec == null) {
            throw new ErrorException("计费事实币种缺少币种规则: " + fact.getCurrency());
        }
        return new BillingFactContext(fact, billingFactRefRepository.findByFactId(factId),
            billingFactLineRepository.findByFactId(factId), null, spec.getMinorUnit());
    }

    /** 首次发布确认时写入 published_at。 */
    @Override
    public boolean markPublished(BillingFactContext context, Instant now) {
        return billingFactRepository.markPublished(context.getFact(), now);
    }
}
