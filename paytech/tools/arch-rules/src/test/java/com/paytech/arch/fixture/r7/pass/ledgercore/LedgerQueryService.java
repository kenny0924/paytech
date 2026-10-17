package com.paytech.arch.fixture.r7.pass.ledgercore;

import com.paytech.arch.fixture.r7.pass.paymentapi.PaymentQueryApi;

public class LedgerQueryService {

    private final PaymentQueryApi paymentQueryApi;

    public LedgerQueryService(PaymentQueryApi paymentQueryApi) {
        this.paymentQueryApi = paymentQueryApi;
    }

    public String paymentStatus(String paymentId) {
        return paymentQueryApi.status(paymentId);
    }
}
