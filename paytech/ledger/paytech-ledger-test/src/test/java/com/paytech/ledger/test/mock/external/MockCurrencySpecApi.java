package com.paytech.ledger.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.test.mocker.external.CurrencySpecMocker;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

import lombok.AllArgsConstructor;

/**
 * 在独立 Mock 服务继承 platform-support 正式查询合同 {@code /platform/v1/currency-specs}（无 Context Path）；ledger 只调用列表，
 * 单个币种查询记为意外。
 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCurrencySpecApi implements CurrencySpecQueryApi {
    private final CurrencySpecMocker mocker;

    @Override
    public CurrencySpecListResponseBody listCurrencySpecs() {
        MockRequestId.mark(CurrencySpecMocker.REQUEST_ID);
        return mocker.answerList();
    }

    @Override
    public CurrencySpecResponseBody getCurrencySpec(String currency) {
        MockRequestId.mark(CurrencySpecMocker.REQUEST_ID);
        throw mocker.unexpectedCall("getCurrencySpec " + currency);
    }
}
