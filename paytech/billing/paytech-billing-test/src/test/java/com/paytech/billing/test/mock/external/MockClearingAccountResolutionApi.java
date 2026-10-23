package com.paytech.billing.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.test.mocker.external.ClearingAccountMocker;
import com.paytech.reconciliation.api.clearing.ClearingAccountResolutionApi;
import com.paytech.reconciliation.api.clearing.request.ResolveClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 reconciliation 正式清算账户解析合同 {@code /reconciliation/v1/clearing-accounts/resolutions}。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockClearingAccountResolutionApi implements ClearingAccountResolutionApi {
    private final ClearingAccountMocker mocker;

    @Override
    public ClearingAccountResolutionResponseBody resolveClearingAccount(ResolveClearingAccountRequestBody body) {
        MockCurrencySpecApi.markRequestId();
        return mocker.resolve(body.getMidId());
    }
}
