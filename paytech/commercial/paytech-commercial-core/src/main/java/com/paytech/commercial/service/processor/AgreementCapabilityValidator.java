package com.paytech.commercial.service.processor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.service.processor.request.AgreementCapabilityTermsRequest;
import com.paytech.commercial.service.processor.request.AgreementCapabilityTermsRequest.Condition;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 开通项新增与修改共用的输入规则（technique.md §4.2）；无副作用，只依赖请求与已读取的目录可配维度
 * <li>生效区间合法；保证金比例与天数成组且比例在 0–1；接受条件每维度一项、取值不重复且按维度格式规范化。</li>
 * <li>维度须在该可开通项可配维度内，必填维度（CURRENCY 等）必须给出。</li>
 */
final class AgreementCapabilityValidator {
    /** 取值为 ISO 3166-1 alpha-2 的国家类维度。 */
    static final Set<PricingDimension> COUNTRY_DIMENSIONS = Set.of(PricingDimension.CONSUMER_COUNTRY,
        PricingDimension.ISSUER_COUNTRY, PricingDimension.DESTINATION_COUNTRY);

    /** 纯静态工具。 */
    private AgreementCapabilityValidator() {
    }

    /**
     * 校验只依赖输入的开通项条款
     *
     * @param terms 开通项条款
     */
    static void checkTerms(AgreementCapabilityTermsRequest terms) {
        if (terms.getEffectiveTo() != null && !terms.getEffectiveTo().isAfter(terms.getEffectiveFrom())) {
            throw new BizCheckFailException("开通项生效终点必须晚于起点");
        }
        BigDecimal rate = terms.getRollingReserveRate();
        if ((rate == null) != (terms.getRollingReserveDays() == null)) {
            throw new BizCheckFailException("滚动保证金比例与天数必须同时提供或同时为空");
        }
        if (rate != null && (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0 || rate.scale() > 10)) {
            throw new BizCheckFailException("滚动保证金比例必须在 0 到 1 之间且最多 10 位小数: " + rate.toPlainString());
        }
        checkConditionValues(terms.getConditions());
    }

    /** 每维度一项、取值不重复且符合维度格式。 */
    static void checkConditionValues(List<Condition> conditions) {
        Set<PricingDimension> seen = new HashSet<>();
        for (Condition condition : conditions) {
            if (!seen.add(condition.getDimensionCode())) {
                throw new InvalidParameterException("conditions 同一维度只能出现一次: " + condition.getDimensionCode().getCode());
            }
            if (new HashSet<>(condition.getValues()).size() != condition.getValues().size()) {
                throw new InvalidParameterException("conditions 取值不得重复: " + condition.getDimensionCode().getCode());
            }
            for (String value : condition.getValues()) {
                if (!validValue(condition.getDimensionCode(), value)) {
                    throw new BizCheckFailException("接受条件取值不符合维度格式: " + condition.getDimensionCode().getCode() + "="
                        + value);
                }
            }
        }
    }

    /**
     * 维度须为该可开通项的可配维度，必填维度必须给出
     *
     * @param conditions 接受条件
     * @param dimensions 目录可配维度
     * @param capabilityCode 可开通项代码
     */
    static void checkConditionsAllowed(List<Condition> conditions, List<ProductCapabilityDimension> dimensions,
        String capabilityCode) {
        Set<PricingDimension> requested = new HashSet<>();
        for (Condition condition : conditions) {
            requested.add(condition.getDimensionCode());
            boolean configurable = dimensions.stream().anyMatch(item -> item.getDimensionCode() == condition.getDimensionCode());
            if (!configurable) {
                throw new BizCheckFailException("维度不在可开通项可配维度内: " + condition.getDimensionCode().getCode()
                    + ", 可开通项: " + capabilityCode);
            }
        }
        for (ProductCapabilityDimension dimension : dimensions) {
            if (Boolean.TRUE.equals(dimension.getRequired()) && !requested.contains(dimension.getDimensionCode())) {
                throw new BizCheckFailException("缺少必填接受条件: " + dimension.getDimensionCode().getCode() + ", 可开通项: "
                    + capabilityCode);
            }
        }
    }

    /** 币种三位大写、币种对为「三位/三位」（与定价画像写法一致，如 USD/AED）、国家类两位大写、其他为大写稳定代码。 */
    static boolean validValue(PricingDimension dimension, String value) {
        if (dimension == PricingDimension.CURRENCY) {
            return value.matches("[A-Z]{3}");
        }
        if (dimension == PricingDimension.CURRENCY_PAIR) {
            return value.matches("[A-Z]{3}/[A-Z]{3}");
        }
        if (COUNTRY_DIMENSIONS.contains(dimension)) {
            return value.matches("[A-Z]{2}");
        }
        return value.matches("[A-Z0-9_]{1,64}");
    }
}
