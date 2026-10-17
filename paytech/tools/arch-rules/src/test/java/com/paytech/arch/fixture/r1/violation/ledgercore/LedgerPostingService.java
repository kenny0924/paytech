package com.paytech.arch.fixture.r1.violation.ledgercore;

import com.paytech.arch.fixture.r1.violation.paymentapp.PaymentApplication;
import com.paytech.arch.fixture.r1.violation.paymentcore.PaymentExecutionService;

public class LedgerPostingService {

    private final PaymentExecutionService paymentExecutionService = new PaymentExecutionService();

    public Class<?> application() {
        return PaymentApplication.class;
    }
}
