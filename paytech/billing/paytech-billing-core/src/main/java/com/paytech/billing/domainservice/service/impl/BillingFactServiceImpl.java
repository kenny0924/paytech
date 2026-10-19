package com.paytech.billing.domainservice.service.impl;

import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domainservice.repository.BillingFactLineRepository;
import com.paytech.billing.domainservice.repository.BillingFactRefRepository;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.service.BillingFactService;
import com.paytech.billing.service.handler.BillingFactRecordedHandler;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** BillingFactService 实现：只访问 mor_billing，事务由 TransactionTemplate 显式表达。 */
@Service
@AllArgsConstructor
public class BillingFactServiceImpl implements BillingFactService {
    private final TransactionTemplate transactionTemplate;
    private final BillingFactRepository billingFactRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final BillingFactRefRepository billingFactRefRepository;
    private final BillingFactLineRepository billingFactLineRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;

    /** 读取事实头、引用、明细行与币种精度；不存在返回空。 */
    @Override
    public BillingFactContext loadContext(long factId) {
        BillingFact fact = billingFactRepository.findById(factId);
        if (fact == null) {
            return null;
        }
        return new BillingFactContext(fact, billingFactRefRepository.findByFactId(factId),
            billingFactLineRepository.findByFactId(factId), null, minorUnit(fact.getCurrency()));
    }

    /** 同一事务写入头、引用与全部明细行；来源唯一键冲突转为并发冲突。 */
    @Override
    public void saveDraft(BillingFactContext context) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                billingFactRepository.insert(context.getFact());
                billingFactRefRepository.insertAll(context.getRefs());
                billingFactLineRepository.insertAll(context.getLines());
            });
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("计费事实来源引用已被并发登记: " + context.getFact().getSourceSystem() + "/"
                + context.getFact().getSourceEventKey(), duplicate);
        }
    }

    /** 同一事务批准、替代并登记发布任务；任一条件更新未命中整体回滚。 */
    @Override
    public void approve(BillingFactContext context, long checkerId, Instant now) {
        long factVersion = context.approvedVersion();
        transactionTemplate.executeWithoutResult(status -> {
            billingFactRepository.approve(context.getFact(), factVersion, checkerId, now);
            if (context.getSupersededFact() != null) {
                billingFactRepository.supersede(context.getSupersededFact(), now);
            }
            compensationEventRegistrar.register(
                BillingFactRecordedHandler.buildEvent(context.getFact().getId(), factVersion));
        });
    }

    /** 币种精度；已登记事实的币种缺少规则说明只读副本被破坏。 */
    int minorUnit(String currency) {
        CurrencySpec spec = currencySpecRepository.findByCurrency(currency);
        if (spec == null) {
            throw new ErrorException("计费事实币种缺少币种规则: " + currency);
        }
        return spec.getMinorUnit();
    }
}
