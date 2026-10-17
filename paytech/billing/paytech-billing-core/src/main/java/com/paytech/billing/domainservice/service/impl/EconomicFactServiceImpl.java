package com.paytech.billing.domainservice.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.core.dal.dataobject.EconomicFactLine;
import com.paytech.billing.domain.EconomicFactBookScope;
import com.paytech.billing.domain.EconomicFactContext;
import com.paytech.billing.domain.view.EconomicFactPageView;
import com.paytech.billing.domain.view.EconomicFactPublicationView;
import com.paytech.billing.domain.view.EconomicFactView;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.repository.EconomicFactLineRepository;
import com.paytech.billing.domainservice.repository.EconomicFactRepository;
import com.paytech.billing.domainservice.service.EconomicFactService;
import com.paytech.billing.service.handler.EconomicFactRecordedHandler;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** EconomicFactService 实现：只访问 mor_billing，事务由 TransactionTemplate 显式表达。 */
@Service
@AllArgsConstructor
public class EconomicFactServiceImpl implements EconomicFactService {
    private final TransactionTemplate transactionTemplate;
    private final CurrencySpecRepository currencySpecRepository;
    private final EconomicFactRepository economicFactRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final EconomicFactLineRepository economicFactLineRepository;

    /** 读取事实头、明细行与币种精度；不存在返回空。 */
    @Override
    public EconomicFactContext loadContext(long factId) {
        EconomicFact fact = economicFactRepository.findById(factId);
        if (fact == null) {
            return null;
        }
        return new EconomicFactContext(fact, economicFactLineRepository.findByEconomics(factId), null,
            minorUnit(fact.getCurrency()));
    }

    /** 同一事务写入头与全部明细行；来源唯一键冲突转为并发冲突。 */
    @Override
    public void saveDraft(EconomicFactContext context) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                economicFactRepository.insert(context.getFact());
                economicFactLineRepository.insertAll(context.getLines());
            });
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("经济事实来源引用已被并发登记: " + context.getFact().getSourceSystem() + "/"
                + context.getFact().getSourceEventKey(), duplicate);
        }
    }

    /** 同一事务批准、替代并登记发布任务；任一条件更新未命中整体回滚。 */
    @Override
    public void approve(EconomicFactContext context, long checkerId, Instant now) {
        long factVersion = context.approvedVersion();
        transactionTemplate.executeWithoutResult(status -> {
            economicFactRepository.approve(context.getFact(), factVersion, checkerId, now);
            if (context.getSupersededFact() != null) {
                economicFactRepository.supersede(context.getSupersededFact(), now);
            }
            compensationEventRegistrar.register(
                EconomicFactRecordedHandler.buildEvent(context.getFact().getId(), factVersion));
        });
    }

    /** 查询事实详情；不存在时业务拒绝。 */
    @Override
    public EconomicFactView getFact(long factId) {
        EconomicFactContext context = loadContext(factId);
        if (context == null) {
            throw new BizCheckFailException("经济事实不存在: " + factId);
        }
        return context.toView();
    }

    /** 一次读取本页事实的全部明细行后逐条组装；本页满 limit 条时以最后一条 ID 作为下一页游标，空页不读取币种规则。 */
    @Override
    public EconomicFactPageView listRecordedFacts(EconomicFactBookScope scope, long fromFactId, int limit) {
        List<EconomicFact> facts = economicFactRepository.findRecordedByBook(scope, fromFactId, limit);
        if (facts.isEmpty()) {
            return new EconomicFactPageView(List.of(), null);
        }
        Map<Long, List<EconomicFactLine>> linesByFact =
            economicFactLineRepository.findByEconomicsIds(facts.stream().map(EconomicFact::getId).toList()).stream()
                .collect(Collectors.groupingBy(EconomicFactLine::getTransactionEconomicsId));
        int minorUnit = minorUnit(scope.getCurrency());
        List<EconomicFactView> items = facts.stream().map(fact -> new EconomicFactContext(fact,
            linesByFact.getOrDefault(fact.getId(), List.of()), null, minorUnit).toView()).toList();
        Long next = facts.size() == limit ? facts.getLast().getId() : null;
        return new EconomicFactPageView(items, next);
    }

    /** 账簿主体下已批准事实的发布进度。 */
    @Override
    public EconomicFactPublicationView getPublicationProgress(EconomicFactBookScope scope) {
        return economicFactRepository.publicationProgress(scope);
    }

    /** 币种精度；已登记事实的币种缺少规则说明只读副本被破坏。 */
    int minorUnit(String currency) {
        CurrencySpec spec = currencySpecRepository.findByCurrency(currency);
        if (spec == null) {
            throw new ErrorException("经济事实币种缺少币种规则: " + currency);
        }
        return spec.getMinorUnit();
    }
}
