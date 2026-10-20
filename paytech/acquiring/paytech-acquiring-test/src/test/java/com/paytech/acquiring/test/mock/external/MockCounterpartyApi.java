package com.paytech.acquiring.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.acquiring.test.mocker.external.CounterpartyMocker;
import com.paytech.counterparty.api.customer.CounterpartyCustomerApi;
import com.paytech.counterparty.api.customer.request.ResolveCustomerRequestBody;
import com.paytech.counterparty.api.customer.response.CardSavingEligibilityResponseBody;
import com.paytech.counterparty.api.customer.response.CustomerSummaryResponseBody;
import com.paytech.counterparty.api.customer.response.ResolveCustomerResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承正式 counterparty 内部合同（counterparty 无 Context Path），由场景配置 DTO。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCounterpartyApi implements CounterpartyCustomerApi {
    private final CounterpartyMocker mocker;

    @Override
    public ResolveCustomerResponseBody resolveCustomer(ResolveCustomerRequestBody requestBody) {
        requestId();
        return mocker.resolve(requestBody.getClientId(), requestBody.getConsumerReference());
    }

    @Override
    public CustomerSummaryResponseBody getCustomer(String customerId) {
        requestId();
        return mocker.customer(customerId);
    }

    @Override
    public CardSavingEligibilityResponseBody cardSavingEligibility(String clientId, String customerId) {
        requestId();
        return mocker.eligibility(clientId, customerId);
    }

    /** 与故障 Problem 中的 requestId 一致，供公共解码核对。 */
    static void requestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", CounterpartyMocker.REQUEST_ID);
    }
}
