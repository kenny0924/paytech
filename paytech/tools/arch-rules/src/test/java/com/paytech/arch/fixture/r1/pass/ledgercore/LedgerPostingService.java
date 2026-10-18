package com.paytech.arch.fixture.r1.pass.ledgercore;

import com.paytech.arch.fixture.r1.pass.paymentapi.PaymentQueryApi;

public class LedgerPostingService {

    private final PaymentQueryApi paymentQueryApi;

    public LedgerPostingService(PaymentQueryApi paymentQueryApi) {
        this.paymentQueryApi = paymentQueryApi;
    }
}
