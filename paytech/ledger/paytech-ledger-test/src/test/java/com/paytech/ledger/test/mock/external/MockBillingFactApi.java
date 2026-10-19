package com.paytech.ledger.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.response.BillingFactListResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;
import com.paytech.ledger.test.mocker.external.BillingFactMocker;

import lombok.AllArgsConstructor;

/**
 * 在独立 Mock 服务继承 billing 正式查询合同 {@code /billing/v1/facts/**}（无 Context Path）；ledger 只调用事实详情与进度，
 * 其余入口记为意外。
 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockBillingFactApi implements BillingFactQueryApi {
    private final BillingFactMocker mocker;

    @Override
    public BillingFactResponseBody getBillingFact(String factId) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        return mocker.answerFact(factId);
    }

    @Override
    public BillingFactListResponseBody listBillingFacts(String entityType, String entityId, String currency,
        String fromFactId, String limit) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        throw mocker.unexpectedCall("listBillingFacts");
    }

    @Override
    public BillingFactProgressResponseBody getBillingFactProgress(String entityType, String entityId, String currency) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        return mocker.answerProgress(entityType, entityId, currency);
    }

    @Override
    public BillingFactListResponseBody listBillingFactsByRef(String refType, String refId, String fromFactId,
        String limit) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        throw mocker.unexpectedCall("listBillingFactsByRef");
    }
}
