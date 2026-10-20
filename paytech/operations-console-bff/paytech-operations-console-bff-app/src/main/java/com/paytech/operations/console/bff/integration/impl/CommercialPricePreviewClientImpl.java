package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.write;

import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.price.request.PricingFactsRequestBody;
import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.ratecard.RateCardApi;
import com.paytech.operations.console.bff.integration.CommercialPricePreviewClient;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.RateCardResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.SimulateCommercialPriceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;

import lombok.AllArgsConstructor;

/**
 * commercial 费率卡与价格命中的 HTTP 转发：每个入口只调用一次 commercial，不重试
 * <li>价格命中要求支付事实追溯 ID；运营试算没有真实支付事实，固定填 SIMULATION_PAYMENT_FACT_ID（commercial 只校验格式、不跨库读取，
 * 命中只读不落库）。</li>
 * <li>commercial 422 的字段路径只投影到本次页面请求体内存在的路径；命中失败原因（CommercialPriceErrorCode）随 Owner detail 透传。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialPricePreviewClientImpl implements CommercialPricePreviewClient {
    /** 运营试算的支付事实追溯 ID：不对应任何真实支付事实。 */
    static final String SIMULATION_PAYMENT_FACT_ID = "1";

    private final RateCardApi rateCardApi;
    private final CommercialPriceApi priceApi;

    /** 费率卡；回执 Client 与请求一致且带合同集合。 */
    @Override
    public RateCardResponseBody rateCard(String clientId, String asOf, String currency) {
        var card = convert(rateCardApi.getRateCard(clientId, asOf, currency), RateCardResponseBody.class);
        requireSame(clientId, card.getClientId());
        require(card.getAgreements() != null);
        return card;
    }

    /** 价格命中；回执版本、OE、币种与请求一致。 */
    @Override
    public CommercialPrice resolve(SimulateCommercialPriceRequestBody request) {
        var price = write(request, () -> priceApi.resolve(ownerRequest(request)), CommercialPrice.class);
        requireSame(request.getTermVersionId(), price.getTermVersionId());
        requireSame(request.getEntityNo(), price.getEntityNo());
        requireSame(request.getCurrency(), price.getCurrency());
        require(price.getComponents() != null && price.getConditions() != null);
        return price;
    }

    /** 页面事实 → commercial 命中请求；维度事实保持键存在与 null 值语义。 */
    static ResolveCommercialPriceRequestBody ownerRequest(SimulateCommercialPriceRequestBody request) {
        var facts = new PricingFactsRequestBody();
        facts.setPaymentMethodCode(request.getPaymentFacts().getPaymentMethodCode());
        facts.setDimensions(new LinkedHashMap<>(request.getPaymentFacts().getDimensions()));
        var owner = new ResolveCommercialPriceRequestBody();
        owner.setTermVersionId(request.getTermVersionId());
        owner.setEntityNo(request.getEntityNo());
        owner.setAgreementId(request.getAgreementId());
        owner.setContextAsOfTime(request.getContextAsOfTime());
        owner.setTermType(request.getTermType());
        owner.setCurrency(request.getCurrency());
        owner.setClientId(request.getClientId());
        owner.setLocalPartnerId(request.getLocalPartnerId());
        owner.setPaymentFactId(SIMULATION_PAYMENT_FACT_ID);
        owner.setPaymentFacts(facts);
        return owner;
    }
}
