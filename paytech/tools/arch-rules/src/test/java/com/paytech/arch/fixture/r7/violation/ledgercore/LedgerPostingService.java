package com.paytech.arch.fixture.r7.violation.ledgercore;

import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.arch.fixture.r7.violation.paymentapi.PaymentQueryApi;

public class LedgerPostingService {

    private final TransactionTemplate transactionTemplate;

    private final PaymentQueryApi paymentQueryApi;

    public LedgerPostingService(TransactionTemplate transactionTemplate, PaymentQueryApi paymentQueryApi) {
        this.transactionTemplate = transactionTemplate;
        this.paymentQueryApi = paymentQueryApi;
    }

    public void post(String paymentId) {
        transactionTemplate.executeWithoutResult(status -> paymentQueryApi.status(paymentId));
    }
}
