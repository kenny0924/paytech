package com.paytech.arch.fixture.r6.pass.ledgerapp;

import com.paytech.arch.fixture.r6.pass.ledgerapi.LedgerBookApi;
import com.paytech.arch.fixture.r6.pass.paymentapi.PaymentQueryApi;

public class LedgerBookController implements LedgerBookApi {

    private final PaymentQueryApi paymentQueryApi;

    public LedgerBookController(PaymentQueryApi paymentQueryApi) {
        this.paymentQueryApi = paymentQueryApi;
    }

    @Override
    public String book(String bookId) {
        return paymentQueryApi.status(bookId);
    }
}
