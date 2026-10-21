package com.paytech.billing.domainservice.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.dataobject.BillingFactLineLink;
import com.paytech.billing.core.dal.dataobject.BillingFactPricing;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.BillingFactPayloads;
import com.paytech.billing.domain.BillingFactSearchCriteria;
import com.paytech.billing.domain.view.BillingFactPageView;
import com.paytech.billing.domain.view.BillingFactProgressView;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.domainservice.repository.BillingFactLineLinkRepository;
import com.paytech.billing.domainservice.repository.BillingFactLineRepository;
import com.paytech.billing.domainservice.repository.BillingFactPricingRepository;
import com.paytech.billing.domainservice.repository.BillingFactRefRepository;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.service.BillingFactQueryService;
import com.paytech.billing.domainservice.service.CurrencyRuleService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** BillingFactQueryService 实现：只读 mor_billing，一页事实的引用、明细行、定价与链接各用一条批量查询。 */
@Service
@AllArgsConstructor
public class BillingFactQueryServiceImpl implements BillingFactQueryService {
    private final CurrencyRuleService currencyRuleService;
    private final BillingFactRepository billingFactRepository;
    private final BillingFactRefRepository billingFactRefRepository;
    private final BillingFactLineRepository billingFactLineRepository;
    private final BillingFactPricingRepository billingFactPricingRepository;
    private final BillingFactLineLinkRepository billingFactLineLinkRepository;

    /** 查询事实详情；不存在时业务拒绝。 */
    @Override
    public BillingFactView getFact(long factId) {
        BillingFact fact = billingFactRepository.findById(factId);
        if (fact == null) {
            throw new BizCheckFailException("计费事实不存在: " + factId);
        }
        return views(List.of(fact)).getFirst();
    }

    /** 账簿主体下的已批准事实一页。 */
    @Override
    public BillingFactPageView listRecordedFacts(BillingFactBookScope scope, long fromFactId, int limit) {
        return page(billingFactRepository.findRecordedByBook(scope, fromFactId, limit), limit);
    }

    /** 先按引用索引取一页事实 ID，再批量读取事实。 */
    @Override
    public BillingFactPageView listFactsByRef(FactRefType refType, String refId, long fromFactId, int limit) {
        List<Long> factIds = billingFactRefRepository.findFactIdsByRef(refType, refId, fromFactId, limit);
        return page(billingFactRepository.findByIds(factIds), limit);
    }

    /** 账簿主体下已批准事实的发布进度。 */
    @Override
    public BillingFactProgressView getPublicationProgress(BillingFactBookScope scope) {
        return billingFactRepository.publicationProgress(scope);
    }

    /** 管理列表一页与总数；非空页按查询币种精度补齐总额。 */
    @Override
    public PagingResponseBody<BillingFact> searchFacts(BillingFactSearchCriteria criteria, PagingRequestBody paging) {
        List<BillingFact> facts = billingFactRepository.findSearchPage(criteria, paging.size(), paging.offset());
        if (!facts.isEmpty()) {
            int minorUnit = minorUnit(criteria.getCurrency());
            facts.forEach(fact -> fact.setGrossAmount(BillingFactPayloads.scaled(fact.getGrossAmount(), minorUnit)));
        }
        return new PagingResponseBody<>(facts, paging.page(), paging.size(), billingFactRepository.countSearch(criteria));
    }

    /** 一页事实：满 limit 条时以最后一条 ID 作为下一页游标，空页不读取其他表。 */
    BillingFactPageView page(List<BillingFact> facts, int limit) {
        if (facts.isEmpty()) {
            return new BillingFactPageView(List.of(), null);
        }
        Long next = facts.size() == limit ? facts.getLast().getId() : null;
        return new BillingFactPageView(views(facts), next);
    }

    /** 按输入顺序组装一组事实的详情。 */
    List<BillingFactView> views(List<BillingFact> facts) {
        List<Long> factIds = facts.stream().map(BillingFact::getId).toList();
        Map<Long, List<BillingFactRef>> refs = billingFactRefRepository.findByFactIds(factIds).stream()
            .collect(Collectors.groupingBy(BillingFactRef::getFactId));
        List<BillingFactLine> allLines = billingFactLineRepository.findByFactIds(factIds);
        Map<Long, List<BillingFactLine>> lines = allLines.stream().collect(Collectors.groupingBy(BillingFactLine::getFactId));
        Map<Long, BillingFactPricing> pricing = billingFactPricingRepository.findByFactIds(factIds).stream()
            .collect(Collectors.toMap(BillingFactPricing::getFactId, Function.identity()));
        Map<Long, List<BillingFactLineLink>> links = linksByFact(allLines);
        Map<String, Integer> minorUnits = new HashMap<>();
        return facts.stream().map(fact -> BillingFactView.of(
            new BillingFactContext(fact, refs.getOrDefault(fact.getId(), List.of()),
                lines.getOrDefault(fact.getId(), List.of()), null,
                minorUnits.computeIfAbsent(fact.getCurrency(), this::minorUnit)),
            pricing.get(fact.getId()), links.getOrDefault(fact.getId(), List.of()))).toList();
    }

    /** 以一组明细行为新行的链接，按新行所属事实分组。 */
    Map<Long, List<BillingFactLineLink>> linksByFact(List<BillingFactLine> lines) {
        Map<Long, Long> factOfLine = lines.stream()
            .collect(Collectors.toMap(BillingFactLine::getId, BillingFactLine::getFactId));
        return billingFactLineLinkRepository.findByTargetLineIds(factOfLine.keySet()).stream()
            .collect(Collectors.groupingBy(link -> factOfLine.get(link.getTargetLineId())));
    }

    /** 币种精度：读 Platform 币种快照（只读内存），已登记事实的币种按启用过判断。 */
    int minorUnit(String currency) {
        return currencyRuleService.requireUsable(currency).getMinorUnit();
    }
}
