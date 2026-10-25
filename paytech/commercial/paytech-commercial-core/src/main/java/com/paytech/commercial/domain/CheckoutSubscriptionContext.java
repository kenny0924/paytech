package com.paytech.commercial.domain;

import static com.paytech.commercial.domain.CheckoutResolutionContext.applicable;
import static com.paytech.commercial.domain.CheckoutResolutionContext.require;
import static com.paytech.commercial.domain.CheckoutResolutionContext.unique;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.Agreement;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * CLIENT 合同的产品订阅与本次支付方式命中的开通项（technique §2.5）；只使用同一次本地读取的订阅、开通项、约束与目录。
 * <li>订阅必须含 ACQUIRING；给出支付方式时，同合同同 OE 下至少一个开通项命中即通过，零个则候选落空。</li>
 * <li>未给支付方式时取全部 PAYMENT_METHOD 开通项，同样按状态、时间窗、条件与生效价格过滤；结果为空不落空，不影响 OE 选择。</li>
 * <li>开通项按目录 payment_method_code 匹配本次支付方式；卡组织等在录卡时才知道的维度不在解析时过滤，命中的开通项连同完整接受条件与 3DS
 * 要求一起返回，由收单在执行时使用（二期）。只有目录或开通项重复这类数据冲突才报 409。</li>
 * <li>条件只检查判定时点为 CHECKOUT 的事实：CURRENCY、CONSUMER_COUNTRY 有条件时必须包含请求值。</li>
 * <li>Client 条款版本确定后，只保留有生效价格的开通项（同 OE、画像支付方式相等、币种被接受、画像条件相容的价格）。</li>
 */
@Value
@Accessors(fluent = true)
public class CheckoutSubscriptionContext {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutSubscriptionContext.class);

    /** 合同订阅的产品，按 Code 升序。 */
    List<ProductCode> products;

    /** 本次支付方式命中的全部开通项，按开通项代码升序；请求未给支付方式时为全部可用的 PAYMENT_METHOD 开通项，可为空。 */
    List<MatchedCapability> capabilities;

    /**
     * 判断合同订阅与开通项，候选落空返回 null 并记录原因。
     *
     * @param context 本次解析的查询与本地事实
     * @param agreement 已签署且适用的 CLIENT 合同
     * @param entityNo 候选 OE
     * @return 订阅与命中的开通项；未订阅 ACQUIRING，或给出支付方式而无命中时为 null
     */
    static CheckoutSubscriptionContext resolve(CheckoutResolutionContext context, Agreement agreement, String entityNo) {
        List<ProductCode> products = context.facts().agreementProducts().stream()
            .filter(product -> product.getAgreementId().equals(agreement.agreementId()))
            .map(AgreementProduct::getProductCode).sorted(Comparator.comparing(ProductCode::getCode)).toList();
        if (!products.contains(ProductCode.ACQUIRING)) {
            LOG.info("checkout 候选落空：合同未订阅 ACQUIRING clientId={} entityNo={} agreementId={}",
                context.query().clientId(), entityNo, agreement.agreementId());
            return null;
        }
        List<MatchedCapability> capabilities = context.facts().agreementCapabilities().stream()
            .filter(value -> value.getAgreementId().equals(agreement.agreementId()) && value.getEntityNo().equals(entityNo))
            .map(value -> new MatchedCapability(value, catalog(context, value), conditions(context, value)))
            .filter(value -> matches(context, value))
            .sorted(Comparator.comparing(value -> value.capability().getCapabilityCode())).toList();
        require(capabilities.stream().map(value -> value.capability().getCapabilityCode()).distinct()
            .count() == capabilities.size());
        if (capabilities.isEmpty() && context.query().paymentMethodCode() != null) {
            LOG.info("checkout 候选落空：无可用开通项 clientId={} entityNo={} agreementId={} paymentMethodCode={} currency={}"
                + " country={}", context.query().clientId(), entityNo, agreement.agreementId(),
                context.query().paymentMethodCode(), context.query().currency(), context.query().country());
            return null;
        }
        return new CheckoutSubscriptionContext(products, capabilities);
    }

    /** 开通项引用的唯一目录行；缺失或重复属于数据冲突。 */
    private static ProductCapability catalog(CheckoutResolutionContext context, AgreementCapability capability) {
        ProductCapability catalog = unique(context.facts().productCapabilities(),
            value -> value.getCapabilityCode().equals(capability.getCapabilityCode()));
        require(catalog != null);
        return catalog;
    }

    /** 目录为支付方式类（给出支付方式时须相等）、状态 ENABLED、商业时点在区间内且请求事实满足约束。 */
    private static boolean matches(CheckoutResolutionContext context, MatchedCapability matched) {
        AgreementCapability capability = matched.capability();
        ProductCapability catalog = matched.catalog();
        String paymentMethodCode = context.query().paymentMethodCode();
        if (catalog.getCapabilityKind() != CapabilityKind.PAYMENT_METHOD
            || paymentMethodCode != null && !paymentMethodCode.equals(catalog.getPaymentMethodCode())) {
            return false;
        }
        CheckoutQuery query = context.query();
        return capability.getStatus() == AgreementCapabilityStatus.ENABLED
            && applicable(capability.getEffectiveFrom(), capability.getEffectiveTo(), query.asOf())
            && matched.conditions().allows(PricingDimension.CURRENCY, query.currency())
            && matched.conditions().allows(PricingDimension.CONSUMER_COUNTRY, query.country());
    }

    /** 开通项的接受条件行。 */
    private static CapabilityConditions conditions(CheckoutResolutionContext context, AgreementCapability capability) {
        List<AgreementCapabilityLimit> limits = context.facts().capabilityLimits().stream()
            .filter(limit -> limit.getAgreementCapabilityId().equals(capability.getAgreementCapabilityId())).toList();
        return CapabilityConditions.of(limits);
    }

    /**
     * 只保留在 Client 条款版本中有生效价格的开通项
     *
     * @param context 本次解析的查询与本地事实
     * @param version 商业时点适用的 Client 条款版本
     * @param entityNo 候选 OE
     * @return 过滤后的订阅；给出支付方式而无任何有价开通项时为 null
     */
    CheckoutSubscriptionContext priced(CheckoutResolutionContext context, CommercialTermVersion version, String entityNo) {
        List<CommercialTermPrice> prices = context.facts().termPrices().stream()
            .filter(price -> price.termVersionId().equals(version.termVersionId()) && price.entityNo().equals(entityNo))
            .toList();
        List<MatchedCapability> priced = capabilities.stream().filter(matched -> prices.stream()
            .anyMatch(price -> hasPrice(context, matched, price))).toList();
        if (priced.isEmpty() && context.query().paymentMethodCode() != null) {
            LOG.info("checkout 候选落空：开通项无生效价格 clientId={} entityNo={} versionId={} paymentMethodCode={}",
                context.query().clientId(), entityNo, version.termVersionId(), context.query().paymentMethodCode());
            return null;
        }
        return new CheckoutSubscriptionContext(products, priced);
    }

    /** 价格画像支付方式等于开通项支付方式、币种被接受且画像条件与接受条件相容。 */
    private static boolean hasPrice(CheckoutResolutionContext context, MatchedCapability matched, CommercialTermPrice price) {
        PricingProfile profile = unique(context.facts().methodProfiles(),
            value -> value.pricingProfileCode().equals(price.pricingProfileCode()));
        if (profile == null || !java.util.Objects.equals(profile.paymentMethodCode(),
            matched.catalog().getPaymentMethodCode())) {
            return false;
        }
        java.util.Map<PricingDimension, java.util.Set<String>> profileConditions = new java.util.EnumMap<>(PricingDimension.class);
        context.facts().profileConditions().stream()
            .filter(condition -> condition.getPricingProfileId().equals(profile.pricingProfileId()))
            .forEach(condition -> profileConditions.computeIfAbsent(condition.getDimensionCode(),
                key -> new java.util.TreeSet<>()).add(condition.getConditionValue()));
        return matched.conditions().allows(PricingDimension.CURRENCY, price.currency())
            && matched.conditions().compatibleWith(profileConditions);
    }

    /** 命中的合同开通项、目录行与接受条件。 */
    @Value
    @Accessors(fluent = true)
    public static class MatchedCapability {
        /** 合同在该 OE 下的开通项。 */
        AgreementCapability capability;

        /** 开通项引用的可开通项目录（支付方式与种类）。 */
        ProductCapability catalog;

        /** 开通项接受条件。 */
        CapabilityConditions conditions;
    }
}
