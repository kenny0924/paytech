package com.paytech.commercial.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.price.CommercialTermVersionPriceApi;
import com.paytech.commercial.api.price.response.CommercialTermVersionPricesResponseBody;
import com.paytech.commercial.domainservice.service.CommercialPriceResolutionService;

import lombok.AllArgsConstructor;

/** 固定版本价格全集的 HTTP 协议适配，直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class CommercialTermVersionPriceController implements CommercialTermVersionPriceApi {
    private final CommercialPriceResolutionService commercialPriceResolutionService;

    /** 版本下某 OE 的全部价格。 */
    @Override
    public CommercialTermVersionPricesResponseBody listPrices(String versionId, String entityNo, String termType,
        String currency) {
        var prices = commercialPriceResolutionService.listVersionPrices(Long.parseLong(versionId), entityNo, termType,
            currency);
        CommercialTermVersionPricesResponseBody body = new CommercialTermVersionPricesResponseBody();
        body.setTermVersionId(versionId);
        body.setTermType(termType);
        body.setEntityNo(entityNo);
        body.setPrices(prices.stream().map(CommercialPriceConverter::toResponseBody).toList());
        return body;
    }
}
