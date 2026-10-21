package com.paytech.commercial.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PriceRelevanceReason;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

/**
 * 合同开通项、其目录行与接受条件的组合，承载价格与开通项是否相关的纯判断
 * <li>相关：画像支付方式等于目录支付方式（非支付方式类两者均为空）、价格币种被 CURRENCY 条件接受、画像条件与接受条件相容。</li>
 * <li>不访问数据库；由调用方一次读取开通项、条件行与目录后构造。</li>
 */
public final class CapabilityAcceptance {
    private final AgreementCapability capability;

    private final ProductCapability catalog;

    private final CapabilityConditions conditions;

    /**
     * 组合开通项、目录与条件。
     *
     * @param capability 合同开通项
     * @param catalog 开通项引用的目录行
     * @param conditions 接受条件
     */
    public CapabilityAcceptance(AgreementCapability capability, ProductCapability catalog, CapabilityConditions conditions) {
        this.capability = Objects.requireNonNull(capability, "capability");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    /**
     * 由合同全部开通项、条件行与目录构造；目录缺失的开通项（数据异常）被跳过
     *
     * @param capabilities 合同开通项
     * @param limits 这些开通项的全部条件行
     * @param catalogs 目录行
     * @return 按输入顺序的组合
     */
    public static List<CapabilityAcceptance> of(List<AgreementCapability> capabilities, List<AgreementCapabilityLimit> limits,
        List<ProductCapability> catalogs) {
        return capabilities.stream().map(capability -> {
            ProductCapability catalog = catalogs.stream()
                .filter(item -> item.getCapabilityCode().equals(capability.getCapabilityCode())).findFirst().orElse(null);
            if (catalog == null) {
                return null;
            }
            return new CapabilityAcceptance(capability, catalog, CapabilityConditions.of(limits.stream()
                .filter(limit -> limit.getAgreementCapabilityId().equals(capability.getAgreementCapabilityId())).toList()));
        }).filter(Objects::nonNull).toList();
    }

    /** 合同开通项。 */
    public AgreementCapability capability() {
        return capability;
    }

    /** 目录行。 */
    public ProductCapability catalog() {
        return catalog;
    }

    /** 接受条件。 */
    public CapabilityConditions conditions() {
        return conditions;
    }

    /** 开通项种类。 */
    public CapabilityKind kind() {
        return catalog.getCapabilityKind();
    }

    /** 状态为 ENABLED。 */
    public boolean enabled() {
        return capability.getStatus() == AgreementCapabilityStatus.ENABLED;
    }

    /** 开通项在某 OE 下。 */
    public boolean on(String entityNo) {
        return capability.getEntityNo().equals(entityNo);
    }

    /**
     * 价格与本开通项的不相关原因；相关返回 null
     *
     * @param candidate 价格、画像与条件
     * @return PAYMENT_METHOD_NOT_ENABLED、CURRENCY_NOT_ENABLED、CONDITION_OUT_OF_SCOPE 或 null
     */
    public PriceRelevanceReason mismatch(CommercialPriceCandidate candidate) {
        if (!candidate.appliesTo(catalog.getPaymentMethodCode())) {
            return PriceRelevanceReason.PAYMENT_METHOD_NOT_ENABLED;
        }
        if (!conditions.allows(PricingDimension.CURRENCY, candidate.price().getCurrency())) {
            return PriceRelevanceReason.CURRENCY_NOT_ENABLED;
        }
        return conditions.compatibleWith(profileConditions(candidate)) ? null : PriceRelevanceReason.CONDITION_OUT_OF_SCOPE;
    }

    /** 画像条件按维度汇总为取值集合。 */
    public static Map<PricingDimension, Set<String>> profileConditions(CommercialPriceCandidate candidate) {
        Map<PricingDimension, Set<String>> values = new EnumMap<>(PricingDimension.class);
        candidate.conditions().forEach(condition -> values.computeIfAbsent(condition.getDimensionCode(),
            key -> new TreeSet<>()).add(condition.getConditionValue()));
        return values;
    }
}
