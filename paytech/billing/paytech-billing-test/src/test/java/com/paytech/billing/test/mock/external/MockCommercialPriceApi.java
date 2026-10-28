package com.paytech.billing.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.test.mocker.external.CommercialPriceMocker;
import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 commercial 正式价格合同 {@code /commercial-prices/v1}；billing 只调用命中，按价格 ID 回读记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCommercialPriceApi implements CommercialPriceApi {
    private final CommercialPriceMocker mocker;

    @Override
    public CommercialPriceResponseBody resolve(ResolveCommercialPriceRequestBody body) {
        MockRequestId.mark();
        return mocker.resolve(body);
    }

    @Override
    public CommercialPriceResponseBody getPrice(String priceId) {
        MockRequestId.mark();
        throw mocker.unexpectedCall("getPrice " + priceId);
    }
}
