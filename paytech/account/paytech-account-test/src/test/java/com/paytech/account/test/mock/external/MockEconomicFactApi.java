package com.paytech.account.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.test.mocker.external.BillingFactMocker;
import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.request.ApproveEconomicFactRequestBody;
import com.paytech.billing.api.request.SubmitEconomicFactRequestBody;
import com.paytech.billing.api.response.EconomicFactListResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 billing 正式合同（无 Context Path）；account 只调用事实列表，其余入口记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockEconomicFactApi implements EconomicFactCommandApi {
    private final BillingFactMocker mocker;

    @Override
    public ResponseEntity<EconomicFactResponseBody> submitEconomicFact(SubmitEconomicFactRequestBody body) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        throw mocker.unexpectedCall("submitEconomicFact");
    }

    @Override
    public EconomicFactResponseBody approveEconomicFact(String factId, ApproveEconomicFactRequestBody body) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        throw mocker.unexpectedCall("approveEconomicFact");
    }

    @Override
    public EconomicFactResponseBody getEconomicFact(String factId) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        throw mocker.unexpectedCall("getEconomicFact");
    }

    @Override
    public EconomicFactListResponseBody listEconomicFacts(String bookEntityType, String bookEntityId, String currency,
        String fromFactId, String limit) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        return mocker.list(bookEntityType, bookEntityId, currency, fromFactId, limit);
    }

    @Override
    public PendingEconomicFactsResponseBody getPendingEconomicFacts(String bookEntityType, String bookEntityId,
        String currency) {
        MockRequestId.mark(BillingFactMocker.REQUEST_ID);
        throw mocker.unexpectedCall("getPendingEconomicFacts");
    }
}
