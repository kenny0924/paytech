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
import com.paytech.commercial.api.enums.CapabilityLimitCode;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.Agreement;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * CLIENT 合同的产品订阅与本次支付方式命中的开通项（technique §2.5）；只使用同一次本地读取的订阅、开通项、约束与目录。
 * <li>订阅必须含 ACQUIRING；给出支付方式时，同合同同 OE 下至少一个开通项命中即通过，零个则候选落空。</li>
 * <li>Hosted Checkout 在解析时还不知道卡品牌等维度，带 dimension 的目录项（如 CARD_VISA 的 CARD_BRAND=VISA）按其 payment_method_code
 * 参与匹配；命中的全部开通项连同维度一起返回，由支付尝试按实际品牌选用。只有目录或开通项重复这类数据冲突才报 409。</li>
 * <li>约束只检查请求已带的事实：CURRENCY、CONSUMER_COUNTRY 有行时必须包含请求值；REQUIRE_3DS 是执行要求，不在解析时过滤。</li>
 */
@Value
@Accessors(fluent = true)
public class CheckoutSubscriptionContext {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutSubscriptionContext.class);

    /** 合同订阅的产品，按 Code 升序。 */
    List<ProductCode> products;

    /** 本次支付方式命中的全部开通项，按开通项代码升序；请求未给支付方式时为空列表。 */
    List<MatchedCapability> capabilities;

    /**
     * 判断合同订阅与开通项，候选落空返回 null 并记录原因。
     *
     * @param context 本次解析的查询与本地事实
     * @param agreement 已签署且适用的 CLIENT 合同
     * @param entityNo 候选 OE
     * @return 订阅与命中的开通项；未订阅 ACQUIRING 或支付方式无命中时为 null
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
        if (context.query().paymentMethodCode() == null) {
            return new CheckoutSubscriptionContext(products, List.of());
        }
        List<MatchedCapability> capabilities = context.facts().agreementCapabilities().stream()
            .filter(value -> value.getAgreementId().equals(agreement.agreementId()) && value.getEntityNo().equals(entityNo))
            .map(value -> new MatchedCapability(value, catalog(context, value))).filter(value -> matches(context, value))
            .sorted(Comparator.comparing(value -> value.capability().getCapabilityCode())).toList();
        require(capabilities.stream().map(value -> value.capability().getCapabilityCode()).distinct()
            .count() == capabilities.size());
        if (capabilities.isEmpty()) {
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

    /** 目录为本次支付方式、状态 ENABLED、商业时点在区间内且请求事实满足约束。 */
    private static boolean matches(CheckoutResolutionContext context, MatchedCapability matched) {
        AgreementCapability capability = matched.capability();
        ProductCapability catalog = matched.catalog();
        if (catalog.getCapabilityKind() != CapabilityKind.PAYMENT_METHOD
            || !context.query().paymentMethodCode().equals(catalog.getPaymentMethodCode())) {
            return false;
        }
        CheckoutQuery query = context.query();
        return capability.getStatus() == AgreementCapabilityStatus.ENABLED
            && applicable(capability.getEffectiveFrom(), capability.getEffectiveTo(), query.asOf())
            && limitAllows(context, capability, CapabilityLimitCode.CURRENCY, query.currency())
            && limitAllows(context, capability, CapabilityLimitCode.CONSUMER_COUNTRY, query.country());
    }

    /** 同码多行按 OR：没有该码的约束行即不限制，有行时必须包含请求值。 */
    private static boolean limitAllows(CheckoutResolutionContext context, AgreementCapability capability,
        CapabilityLimitCode limitCode, String value) {
        List<String> allowed = context.facts().capabilityLimits().stream()
            .filter(limit -> limit.getAgreementCapabilityId().equals(capability.getAgreementCapabilityId())
                && limit.getLimitCode() == limitCode)
            .map(AgreementCapabilityLimit::getLimitValue).toList();
        return allowed.isEmpty() || allowed.contains(value);
    }

    /** 命中的合同开通项及其目录行（提供维度）。 */
    @Value
    @Accessors(fluent = true)
    public static class MatchedCapability {
        /** 合同在该 OE 下的开通项。 */
        AgreementCapability capability;

        /** 开通项引用的可开通项目录。 */
        ProductCapability catalog;
    }
}
