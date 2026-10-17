package com.paytech.arch.fixture.r6.violation.ledgerapp;

import com.paytech.arch.fixture.r6.violation.paymentapi.PaymentQueryApi;

public class LocalPaymentQueryApi implements PaymentQueryApi {

    @Override
    public String status(String paymentId) {
        return "SUCCEEDED";
    }
}
