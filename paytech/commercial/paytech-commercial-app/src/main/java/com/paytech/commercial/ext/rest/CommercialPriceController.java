package com.paytech.commercial.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;
import com.paytech.commercial.domainservice.service.CommercialPriceResolutionService;
import com.paytech.commercial.service.processor.ResolveCommercialPriceProcessor;

import lombok.AllArgsConstructor;

/** 价格命中与回读的 HTTP 协议适配；回读直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class CommercialPriceController implements CommercialPriceApi {
    private final ResolveCommercialPriceProcessor resolveCommercialPriceProcessor;
    private final CommercialPriceResolutionService commercialPriceResolutionService;

    /** 命中唯一价格。 */
    @Override
    public CommercialPriceResponseBody resolve(ResolveCommercialPriceRequestBody body) {
        return CommercialPriceConverter.toResponseBody(
            resolveCommercialPriceProcessor.process(CommercialPriceConverter.toQuery(body)));
    }

    /** 按价格 ID 回读。 */
    @Override
    public CommercialPriceResponseBody getPrice(String priceId) {
        return CommercialPriceConverter.toResponseBody(commercialPriceResolutionService.getPrice(Long.parseLong(priceId)));
    }
}
