package com.paytech.billing.integration.commercial.impl;

import static com.paytech.billing.integration.UpstreamResponseFields.optionalDecimal;
import static com.paytech.billing.integration.UpstreamResponseFields.required;
import static com.paytech.billing.integration.UpstreamResponseFields.requiredDecimal;
import static com.paytech.billing.integration.UpstreamResponseFields.requiredId;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.billing.domain.CommercialPrice;
import com.paytech.billing.domain.CommercialPriceBand;
import com.paytech.billing.domain.CommercialPriceComponent;
import com.paytech.billing.domain.OrderCommercialContext;
import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.domain.PriceSettlement;
import com.paytech.billing.integration.commercial.CommercialPriceClient;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.price.request.PricingFactsRequestBody;
import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.price.response.CommercialPriceBandResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceComponentResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceSettlementResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * 只适配 commercial-api 价格命中 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>commercial 的 404 / 409 / 422（版本不存在、歧义、事实不完整、无命中、事实不合法）对自动登记都表示"上游事实或配置尚未就绪"：
 * 转为 ErrorException 使事实停在重试并保留原因，由运营补齐后重放（技术方案 4.8）。</li>
 */
@Component
@AllArgsConstructor
public class CommercialPriceClientImpl implements CommercialPriceClient {
    private static final String UPSTREAM = "commercial";

    private final CommercialPriceApi commercialPriceApi;

    /** Client 合同、Client Owner 与 Client 定价版本。 */
    @Override
    public CommercialPrice resolveClientPricing(OrderPaymentSucceededFact payment) {
        OrderCommercialContext order = payment.getOrder();
        ResolveCommercialPriceRequestBody body = baseRequest(CommercialCodes.TermType.CLIENT_PRICING,
            order.getClientPricingVersionId(), order.getClientAgreementId(), payment);
        body.setClientId(Long.toString(order.getClientId()));
        return resolve(body);
    }

    /** Partner 合同、Local Partner Owner 与指定条款版本。 */
    @Override
    public CommercialPrice resolvePartnerTerm(String termType, long termVersionId, OrderPaymentSucceededFact payment) {
        OrderCommercialContext order = payment.getOrder();
        ResolveCommercialPriceRequestBody body =
            baseRequest(termType, termVersionId, order.getPartnerAgreementId(), payment);
        body.setLocalPartnerId(Long.toString(order.getLocalPartnerId()));
        return resolve(body);
    }

    /** 调用命中接口；上游明确拒绝转为可重试错误。 */
    CommercialPrice resolve(ResolveCommercialPriceRequestBody body) {
        CommercialPriceResponseBody response;
        try {
            response = commercialPriceApi.resolve(body);
        } catch (FailException rejected) {
            throw new ErrorException("commercial 价格未命中或条款未就绪: " + body.getTermType() + " 版本 "
                + body.getTermVersionId() + ", 支付尝试 " + body.getPaymentFactId(), rejected);
        }
        return toPrice(required(UPSTREAM, "price", response));
    }

    /** 版本、OE、合同、商业时点、条款类型、币种、支付事实 ID 与受控定价事实。 */
    ResolveCommercialPriceRequestBody baseRequest(String termType, long termVersionId, long agreementId,
        OrderPaymentSucceededFact payment) {
        OrderCommercialContext order = payment.getOrder();
        ResolveCommercialPriceRequestBody body = new ResolveCommercialPriceRequestBody();
        body.setTermVersionId(Long.toString(termVersionId));
        body.setEntityNo(order.getEntityNo());
        body.setAgreementId(Long.toString(agreementId));
        body.setContextAsOfTime(order.getCommercialContextTime().truncatedTo(ChronoUnit.MICROS).toString());
        body.setTermType(termType);
        body.setCurrency(order.getCurrency());
        body.setPaymentFactId(Long.toString(payment.getPaymentAttemptId()));
        body.setPaymentFacts(pricingFacts(payment));
        return body;
    }

    /** 支付方式与非空维度事实；为空的卡属性不传，表示事实未知（4.6.3）。 */
    PricingFactsRequestBody pricingFacts(OrderPaymentSucceededFact payment) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put(PricingDimension.CURRENCY.getCode(), payment.getOrder().getCurrency());
        putIfPresent(dimensions, PricingDimension.CONSUMER_COUNTRY, payment.getOrder().getConsumerCountryCode());
        putIfPresent(dimensions, PricingDimension.CARD_BRAND, payment.getCardBrand());
        putIfPresent(dimensions, PricingDimension.FUNDING_TYPE, payment.getFundingType());
        putIfPresent(dimensions, PricingDimension.CARD_LEVEL, payment.getCardLevel());
        PricingFactsRequestBody facts = new PricingFactsRequestBody();
        facts.setPaymentMethodCode(payment.getPaymentMethodCode());
        facts.setDimensions(dimensions);
        return facts;
    }

    /** 非空时写入维度。 */
    static void putIfPresent(Map<String, String> dimensions, PricingDimension dimension, String value) {
        if (value != null) {
            dimensions.put(dimension.getCode(), value);
        }
    }

    /** 价格 DTO 转为本地价格；组件与档位逐项转换。 */
    CommercialPrice toPrice(CommercialPriceResponseBody response) {
        List<CommercialPriceComponentResponseBody> components = required(UPSTREAM, "components", response.getComponents());
        return CommercialPrice.builder().termType(required(UPSTREAM, "termType", response.getTermType()))
            .termVersionId(requiredId(UPSTREAM, "termVersionId", response.getTermVersionId()))
            .planId(response.getPlanId()).priceId(requiredId(UPSTREAM, "priceId", response.getPriceId()))
            .pricingProfileCode(response.getPricingProfileCode())
            .components(components.stream().map(this::toComponent).toList())
            .settlement(toSettlement(response.getSettlement())).build();
    }

    /** 组件转换。 */
    CommercialPriceComponent toComponent(CommercialPriceComponentResponseBody component) {
        List<CommercialPriceBandResponseBody> bands = component.getBands() == null ? List.of() : component.getBands();
        return CommercialPriceComponent.builder()
            .componentId(requiredId(UPSTREAM, "componentId", component.getComponentId()))
            .componentNo(required(UPSTREAM, "componentNo", component.getComponentNo()))
            .componentCode(component.getComponentCode())
            .componentType(required(UPSTREAM, "componentType", component.getComponentType()))
            .triggerCode(required(UPSTREAM, "triggerCode", component.getTriggerCode()))
            .calculationBase(required(UPSTREAM, "calculationBase", component.getCalculationBase()))
            .rate(optionalDecimal(UPSTREAM, "rate", component.getRate()))
            .fixedAmount(optionalDecimal(UPSTREAM, "fixedAmount", component.getFixedAmount()))
            .currency(component.getCurrency())
            .minAmount(optionalDecimal(UPSTREAM, "minAmount", component.getMinAmount()))
            .maxAmount(optionalDecimal(UPSTREAM, "maxAmount", component.getMaxAmount()))
            .bandBasis(component.getBandBasis()).bandMode(component.getBandMode())
            .bands(bands.stream().map(CommercialPriceClientImpl::toBand).toList()).build();
    }

    /** 档位转换。 */
    static CommercialPriceBand toBand(CommercialPriceBandResponseBody band) {
        return new CommercialPriceBand(requiredId(UPSTREAM, "bandId", band.getBandId()),
            required(UPSTREAM, "bandNo", band.getBandNo()),
            requiredDecimal(UPSTREAM, "lowerBound", band.getLowerBound()),
            optionalDecimal(UPSTREAM, "upperBound", band.getUpperBound()),
            optionalDecimal(UPSTREAM, "rate", band.getRate()),
            optionalDecimal(UPSTREAM, "fixedAmount", band.getFixedAmount()));
    }

    /** 结算四字段；Partner 条款为空。 */
    static PriceSettlement toSettlement(CommercialPriceSettlementResponseBody settlement) {
        if (settlement == null) {
            return null;
        }
        return new PriceSettlement(settlement.getSettlementBusinessDays(), settlement.getBusinessCalendarCode(),
            settlement.getSettlementTimezone(), settlement.getCutoffSecondOfDay());
    }
}
