package com.paytech.commercial.ext.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.price.response.CommercialPriceBandResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceComponentResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceConditionResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceSettlementResponseBody;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.CommercialPriceQuery;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 价格 Wire 转换：ID 为十进制字符串，时间为 UTC RFC 3339，费率为去尾零的十进制字符串，金额按币种精度补齐小数位（不舍入更细的参数）
 * <li>只做协议转换，不查询、不校验业务规则。</li>
 */
final class CommercialPriceConverter {
    /** 纯静态转换。 */
    private CommercialPriceConverter() {
    }

    /** 命中请求 → 内部查询。 */
    static CommercialPriceQuery toQuery(ResolveCommercialPriceRequestBody body) {
        return new CommercialPriceQuery(Long.parseLong(body.getTermVersionId()), body.getEntityNo(),
            Long.parseLong(body.getAgreementId()), Instant.parse(body.getContextAsOfTime()), body.getTermType(),
            body.getCurrency(), id(body.getClientId()), id(body.getLocalPartnerId()),
            body.getPaymentFacts().getPaymentMethodCode(), body.getPaymentFacts().getDimensions());
    }

    /** 价格视图 → 不可变价格对象。 */
    static CommercialPriceResponseBody toResponseBody(CommercialPriceView view) {
        CommercialTermPrice price = view.candidate().price();
        CommercialPriceResponseBody body = new CommercialPriceResponseBody();
        body.setTermType(view.plan().getTermType());
        body.setAgreementId(text(view.version().getAgreementId()));
        body.setPlanId(text(view.plan().getTermPlanId()));
        body.setTermVersionId(text(view.version().getTermVersionId()));
        body.setEntityNo(price.getEntityNo());
        body.setClientId(text(view.plan().getClientId()));
        body.setLocalPartnerId(text(view.plan().getLocalPartnerId()));
        body.setPriceId(text(price.getTermPriceId()));
        body.setPricingProfileCode(price.getPricingProfileCode());
        body.setPaymentMethodCode(view.candidate().profile().getPaymentMethodCode());
        body.setPriorityNo(price.getPriorityNo());
        body.setCurrency(price.getCurrency());
        body.setPricingModel(CodeEnum.defaultCode(view.version().getPricingModel()));
        body.setFeeTaxTreatment(CodeEnum.defaultCode(view.version().getFeeTaxTreatment()));
        body.setConditions(conditions(view.candidate().conditions()));
        body.setComponents(view.components().stream().map(component -> component(view, component)).toList());
        body.setSettlement(view.plan().getClientId() == null ? null : settlement(price));
        return body;
    }

    /** 条件，按维度与值排序。 */
    static List<CommercialPriceConditionResponseBody> conditions(List<PricingProfileCond> conditions) {
        return conditions.stream()
            .sorted(Comparator.comparing((PricingProfileCond condition) -> condition.getDimensionCode().getCode())
                .thenComparing(PricingProfileCond::getConditionValue))
            .map(condition -> {
                CommercialPriceConditionResponseBody body = new CommercialPriceConditionResponseBody();
                body.setDimensionCode(condition.getDimensionCode().getCode());
                body.setConditionValue(condition.getConditionValue());
                return body;
            }).toList();
    }

    /** 组件及其档位。 */
    static CommercialPriceComponentResponseBody component(CommercialPriceView view, CommercialTermComponent component) {
        String currency = component.getCurrency();
        CommercialPriceComponentResponseBody body = new CommercialPriceComponentResponseBody();
        body.setComponentId(text(component.getTermComponentId()));
        body.setComponentNo(component.getComponentNo());
        body.setComponentCode(component.getComponentCode());
        body.setComponentType(CodeEnum.defaultCode(component.getComponentType()));
        body.setTriggerCode(component.getTriggerCode());
        body.setCalculationBase(CodeEnum.defaultCode(component.getCalculationBase()));
        body.setRefundBehavior(CodeEnum.defaultCode(component.getRefundBehavior()));
        body.setRate(rate(component.getRate()));
        body.setFixedAmount(amount(component.getFixedAmount(), currency));
        body.setCurrency(currency);
        body.setMinAmount(amount(component.getMinAmount(), currency));
        body.setMaxAmount(amount(component.getMaxAmount(), currency));
        body.setBandBasis(CodeEnum.defaultCode(component.getBandBasis()));
        body.setBandMode(CodeEnum.defaultCode(component.getBandMode()));
        body.setBands(view.bandsOf(component).stream().map(band -> band(band, currency)).toList());
        return body;
    }

    /** 档位，继承组件币种。 */
    static CommercialPriceBandResponseBody band(CommercialTermComponentBand band, String currency) {
        CommercialPriceBandResponseBody body = new CommercialPriceBandResponseBody();
        body.setBandId(text(band.getTermBandId()));
        body.setBandNo(band.getBandNo());
        body.setLowerBound(amount(band.getLowerBound(), currency));
        body.setUpperBound(amount(band.getUpperBound(), currency));
        body.setRate(rate(band.getRate()));
        body.setFixedAmount(amount(band.getFixedAmount(), currency));
        body.setCurrency(currency);
        return body;
    }

    /** Client 条款结算四字段。 */
    static CommercialPriceSettlementResponseBody settlement(CommercialTermPrice price) {
        CommercialPriceSettlementResponseBody body = new CommercialPriceSettlementResponseBody();
        body.setSettlementBusinessDays(price.getSettlementBusinessDays());
        body.setBusinessCalendarCode(price.getBusinessCalendarCode());
        body.setSettlementTimezone(price.getSettlementTimezone());
        body.setCutoffSecondOfDay(price.getCutoffSecondOfDay());
        return body;
    }

    /** 费率：去尾零的普通十进制。 */
    static String rate(BigDecimal rate) {
        return rate == null ? null : rate.stripTrailingZeros().toPlainString();
    }

    /** 金额：至少补齐到币种精度，更细的参数精度原样保留。 */
    static String amount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        int minorUnits = currency == null ? 0 : Currency.getInstance(currency).getDefaultFractionDigits();
        return stripped.setScale(Math.max(Math.max(minorUnits, 0), Math.max(stripped.scale(), 0))).toPlainString();
    }

    /** 十进制 ID 文本。 */
    static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** 可空十进制 ID。 */
    static Long id(String value) {
        return value == null ? null : Long.valueOf(value);
    }
}
