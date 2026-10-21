package com.paytech.commercial.domainservice.service.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.PriceBenchmarkStatus;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;
import com.paytech.commercial.domain.CapabilityAcceptance;
import com.paytech.commercial.domain.CommercialTermVersionInsight;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.PriceBenchmark;
import com.paytech.commercial.domain.PriceRelevanceIssue;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.repository.TermVersionAcceptanceRepository;
import com.paytech.commercial.domainservice.service.CommercialTermInsightService;
import com.paytech.commercial.domainservice.validator.term.TermPriceRelevance;

import lombok.AllArgsConstructor;

/** CommercialTermInsightService 实现：只读 mor_commercial，相邻一致性读组合。 */
@Service
@AllArgsConstructor
public class CommercialTermInsightServiceImpl implements CommercialTermInsightService {
    private final CommercialPriceViewReader reader;
    private final PriceBenchmarkReader benchmarkReader;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final TermVersionAcceptanceRepository termVersionAcceptanceRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /** 价格与组件 → 破标；来源模板 → 标准价已更新；签署记录；Client 合同方案按当前开通状态计算相关性。 */
    @Override
    public CommercialTermVersionInsight describe(CommercialTermVersionView view) {
        CommercialTermVersion version = view.version();
        if (version == null) {
            return CommercialTermVersionInsight.NONE;
        }
        List<CommercialTermPrice> prices = commercialTermPriceRepository.findByVersionId(version.getTermVersionId());
        List<CommercialTermComponent> components = components(prices);
        Map<Long, PriceBenchmark> benchmarks = benchmarks(prices, components);
        TermVersionAcceptance acceptance = termVersionAcceptanceRepository
            .findByVersionIds(List.of(version.getTermVersionId())).stream().findFirst().orElse(null);
        return new CommercialTermVersionInsight(belowCount(benchmarks), templateUpdate(version), acceptance,
            relevance(view.plan(), version, prices, components), benchmarks);
    }

    /** 当前页价格、组件与来源模板一次读取，计算每个版本的破标数与签署情况。 */
    @Override
    public Map<Long, CommercialTermVersionInsight> summarize(Collection<CommercialTermVersion> versions) {
        List<Long> versionIds = versions.stream().map(CommercialTermVersion::getTermVersionId).toList();
        List<CommercialTermPrice> prices = commercialTermPriceRepository.findByVersionIds(versions.stream()
            .filter(version -> version.getSourceTemplateVersionId() != null).map(CommercialTermVersion::getTermVersionId)
            .toList());
        List<CommercialTermComponent> components = components(prices);
        Map<Long, PriceBenchmark> benchmarks = benchmarks(prices, components);
        Map<Long, TermVersionAcceptance> acceptances = termVersionAcceptanceRepository.findByVersionIds(versionIds).stream()
            .collect(Collectors.toMap(TermVersionAcceptance::getTermVersionId, Function.identity()));
        Map<Long, Long> priceVersions = prices.stream()
            .collect(Collectors.toMap(CommercialTermPrice::getTermPriceId, CommercialTermPrice::getTermVersionId));
        Map<Long, Integer> belowCounts = new HashMap<>();
        components.forEach(component -> {
            if (benchmarks.get(component.getTermComponentId()).getStatus() == PriceBenchmarkStatus.BELOW_STANDARD) {
                belowCounts.merge(priceVersions.get(component.getTermPriceId()), 1, Integer::sum);
            }
        });
        return versionIds.stream().collect(Collectors.toMap(Function.identity(), id -> new CommercialTermVersionInsight(
            belowCounts.getOrDefault(id, 0), null, acceptances.get(id), List.of(), Map.of())));
    }

    /** 按来源模板价格匹配同代码组件后逐个比较。 */
    @Override
    public Map<Long, PriceBenchmark> benchmarks(List<CommercialTermPrice> prices, List<CommercialTermComponent> components) {
        return benchmarkReader.benchmarks(prices, components);
    }

    /** 来源模板方案当前 ACTIVE 版本比来源版本新时返回它。 */
    CommercialTermVersion templateUpdate(CommercialTermVersion version) {
        if (version.getSourceTemplateVersionId() == null) {
            return null;
        }
        CommercialTermVersion source = commercialTermVersionRepository.findById(version.getSourceTemplateVersionId());
        if (source == null) {
            return null;
        }
        return commercialTermVersionRepository.findByPlanId(source.getTermPlanId()).stream()
            .filter(other -> other.getStatus() == CommercialTermVersionStatus.ACTIVE && other.getVersionNo() > source.getVersionNo())
            .max(Comparator.comparing(CommercialTermVersion::getVersionNo)).orElse(null);
    }

    /** Client 合同方案按合同当前开通状态计算相关性；其他方案为空。 */
    List<PriceRelevanceIssue> relevance(CommercialTermPlan plan, CommercialTermVersion version, List<CommercialTermPrice> prices,
        List<CommercialTermComponent> components) {
        if (plan.getOwnerType() != CommercialTermOwnerType.CLIENT || version.getAgreementId() == null || prices.isEmpty()) {
            return List.of();
        }
        List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementId(version.getAgreementId());
        List<CapabilityAcceptance> acceptances = CapabilityAcceptance.of(capabilities,
            agreementCapabilityLimitRepository.findByAgreementCapabilityIds(
                capabilities.stream().map(AgreementCapability::getAgreementCapabilityId).toList()),
            productCapabilityRepository.findByCodes(
                capabilities.stream().map(AgreementCapability::getCapabilityCode).collect(Collectors.toSet())));
        return new TermPriceRelevance(acceptances, productFeeEventRepository.findByTermType(plan.getTermType()))
            .issues(reader.candidates(prices), components);
    }

    /** 一组价格的全部组件。 */
    List<CommercialTermComponent> components(List<CommercialTermPrice> prices) {
        return commercialTermComponentRepository.findByPriceIds(prices.stream().map(CommercialTermPrice::getTermPriceId).toList());
    }

    /** 破标组件数。 */
    static int belowCount(Map<Long, PriceBenchmark> benchmarks) {
        return (int) benchmarks.values().stream().filter(item -> item.getStatus() == PriceBenchmarkStatus.BELOW_STANDARD).count();
    }
}
