package com.paytech.commercial.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domain.CapabilityAcceptance;
import com.paytech.commercial.domain.CapabilityConditions;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.commercial.domain.RateCardQuery;
import com.paytech.commercial.domain.RateCardView;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.service.RateCardQueryService;

import lombok.AllArgsConstructor;

/** RateCardQueryService 实现：只读 mor_commercial。 */
@Service
@AllArgsConstructor
public class RateCardQueryServiceImpl implements RateCardQueryService {
    /** 当前版本。 */
    static final String CURRENT = "CURRENT";

    /** 时点之后生效的已排期版本。 */
    static final String SCHEDULED = "SCHEDULED";

    /** 不进入 Client 费率卡的条款类型：Partner 分润、Partner 买入价与平台服务费。 */
    static final Set<String> EXCLUDED_TERM_TYPES = Set.of(CommercialCodes.TermType.PARTNER_SHARE,
        CommercialCodes.TermType.PARTNER_BUY_RATE, CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE);

    private final AgreementRepository agreementRepository;
    private final CommercialPriceViewReader reader;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final AgreementProductRepository agreementProductRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;

    /** ACTIVE 且在时点生效的 CLIENT 合同逐份组装。 */
    @Override
    public RateCardView load(RateCardQuery query) {
        List<RateCardView.AgreementCard> agreements = agreementRepository.findByClientId(query.getClientId()).stream()
            .filter(agreement -> activeAt(agreement, query.getAsOf()))
            .sorted(Comparator.comparing(Agreement::getAgreementId)).map(agreement -> agreementCard(agreement, query))
            .toList();
        return new RateCardView(query.getClientId(), query.getAsOf(), query.getCurrency(), agreements);
    }

    /** CLIENT 合同、ACTIVE 且区间覆盖时点。 */
    static boolean activeAt(Agreement agreement, Instant asOf) {
        return agreement.getAgreementType() == AgreementAgreementType.CLIENT && agreement.getStatus() == AgreementStatus.ACTIVE
            && !asOf.isBefore(agreement.getEffectiveFrom())
            && (agreement.getEffectiveTo() == null || asOf.isBefore(agreement.getEffectiveTo()));
    }

    /** 一份合同：订阅、开通项与各方案的当前 / 已排期版本。 */
    RateCardView.AgreementCard agreementCard(Agreement agreement, RateCardQuery query) {
        List<ProductCode> products = agreementProductRepository.findByAgreementId(agreement.getAgreementId()).stream()
            .map(AgreementProduct::getProductCode).sorted(Comparator.comparing(ProductCode::getCode)).toList();
        List<RateCardView.CapabilityCard> capabilities = capabilityCards(agreement);
        List<RateCardView.VersionCard> versions = new ArrayList<>();
        for (CommercialTermPlan plan : commercialTermPlanRepository.findByAgreementId(agreement.getAgreementId())) {
            if (EXCLUDED_TERM_TYPES.contains(plan.getTermType()) || plan.getOwnerType() == CommercialTermOwnerType.PLATFORM_TEMPLATE) {
                continue;
            }
            Set<ProductCode> planProducts = productFeeEventRepository.findByTermType(plan.getTermType()).stream()
                .map(ProductFeeEvent::getProductCode).collect(Collectors.toSet());
            List<RateCardView.CapabilityCard> relevant = capabilities.stream()
                .filter(card -> card.getCatalog() != null && planProducts.contains(card.getCatalog().getProductCode())).toList();
            displayedVersions(plan, query.getAsOf()).forEach(entry -> versions.add(new RateCardView.VersionCard(plan,
                entry.version(), entry.timing(), capabilityPrices(plan, entry.version(), relevant, query.getCurrency()))));
        }
        return new RateCardView.AgreementCard(agreement, products, capabilities, versions);
    }

    /** 开通项、目录与约束，按开通项 ID 升序。 */
    List<RateCardView.CapabilityCard> capabilityCards(Agreement agreement) {
        List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementId(agreement.getAgreementId());
        List<AgreementCapabilityLimit> limits = agreementCapabilityLimitRepository.findByAgreementCapabilityIds(
            capabilities.stream().map(AgreementCapability::getAgreementCapabilityId).toList());
        List<ProductCapability> catalog = productCapabilityRepository.findByCodes(
            capabilities.stream().map(AgreementCapability::getCapabilityCode).collect(Collectors.toSet()));
        return capabilities.stream().sorted(Comparator.comparing(AgreementCapability::getAgreementCapabilityId))
            .map(capability -> new RateCardView.CapabilityCard(capability,
                catalog.stream().filter(item -> item.getCapabilityCode().equals(capability.getCapabilityCode())).findFirst()
                    .orElse(null),
                limits.stream().filter(limit -> limit.getAgreementCapabilityId().equals(capability.getAgreementCapabilityId()))
                    .sorted(Comparator.comparing((AgreementCapabilityLimit limit) -> limit.getLimitCode().getCode())
                        .thenComparing(AgreementCapabilityLimit::getLimitValue))
                    .toList()))
            .toList();
    }

    /** 时点适用版本（CURRENT）与时点之后生效的 SCHEDULED 版本，按生效起点排序。 */
    List<TimedVersion> displayedVersions(CommercialTermPlan plan, Instant asOf) {
        CommercialTermVersion current = commercialTermVersionRepository.findEffectiveAt(plan.getTermPlanId(), asOf);
        Stream<TimedVersion> scheduled = commercialTermVersionRepository.findByPlanId(plan.getTermPlanId()).stream()
            .filter(version -> version.getStatus() == CommercialTermVersionStatus.SCHEDULED
                && version.getEffectiveFrom().isAfter(asOf))
            .sorted(Comparator.comparing(CommercialTermVersion::getEffectiveFrom))
            .map(version -> new TimedVersion(version, SCHEDULED));
        return Stream.concat(current == null ? Stream.empty() : Stream.of(new TimedVersion(current, CURRENT)), scheduled)
            .toList();
    }

    /** 版本价格按相关开通项 × 币种分块。 */
    List<RateCardView.CapabilityPrices> capabilityPrices(CommercialTermPlan plan, CommercialTermVersion version,
        List<RateCardView.CapabilityCard> capabilities, String currencyFilter) {
        List<CommercialPriceView> views = reader.views(plan, version,
            reader.candidates(commercialTermPriceRepository.findByVersionId(version.getTermVersionId())));
        return capabilities.stream().map(card -> new RateCardView.CapabilityPrices(card,
            currencyBlocks(card, views, currencyFilter))).toList();
    }

    /** 开通项的币种块：有 CURRENCY 约束时按约束，否则按价格中出现的币种；块内具体度降序、priorityNo 升序。 */
    static List<RateCardView.CurrencyPrices> currencyBlocks(RateCardView.CapabilityCard card, List<CommercialPriceView> views,
        String currencyFilter) {
        List<CommercialPriceView> applicable = views.stream().filter(view -> applies(card, view.candidate())).toList();
        List<String> limited = card.getLimits().stream().filter(limit -> limit.getLimitCode() == PricingDimension.CURRENCY)
            .map(AgreementCapabilityLimit::getLimitValue).toList();
        Stream<String> currencies = limited.isEmpty()
            ? applicable.stream().map(view -> view.candidate().price().getCurrency()) : limited.stream();
        return currencies.distinct().sorted().filter(currency -> currencyFilter == null || currencyFilter.equals(currency))
            .map(currency -> new RateCardView.CurrencyPrices(currency, applicable.stream()
                .filter(view -> view.candidate().price().getCurrency().equals(currency))
                .sorted((left, right) -> CommercialPriceCandidate.compareForHit(left.candidate(), right.candidate()))
                .toList()))
            .toList();
    }

    /** 价格适用于开通项：同 OE、支付方式精确、画像条件与开通项接受条件相容（币种由币种块筛选）。 */
    static boolean applies(RateCardView.CapabilityCard card, CommercialPriceCandidate candidate) {
        ProductCapability catalog = card.getCatalog();
        return candidate.price().getEntityNo().equals(card.getCapability().getEntityNo())
            && candidate.appliesTo(catalog.getPaymentMethodCode())
            && CapabilityConditions.of(card.getLimits()).compatibleWith(CapabilityAcceptance.profileConditions(candidate));
    }

    /** 带展示时机的版本。 */
    record TimedVersion(CommercialTermVersion version, String timing) {
    }
}
