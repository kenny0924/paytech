package com.paytech.billing.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.billing.test.mocker.external.CurrencySpecMocker;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

import lombok.AllArgsConstructor;

/**
 * 在独立 Mock 服务继承 platform-support 正式币种规则查询合同 {@code /platform/v1/currency-specs}（无 Context Path）；billing 只调用全量列表，
 * 单个币种查询记为意外。
 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCurrencySpecApi implements CurrencySpecQueryApi {
    private final CurrencySpecMocker mocker;

    @Override
    public CurrencySpecListResponseBody listCurrencySpecs() {
        markRequestId();
        return mocker.answerList();
    }

    @Override
    public CurrencySpecResponseBody getCurrencySpec(String currency) {
        markRequestId();
        throw mocker.unexpectedCall("getCurrencySpec " + currency);
    }

    /** 公共客户端按 Problem Profile 校验响应头与 Problem 体的 requestId 一致，Mock 响应统一写入固定 Request ID。 */
    static void markRequestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", CurrencySpecMocker.REQUEST_ID);
    }
}
