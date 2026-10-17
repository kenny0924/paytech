package com.paytech.arch.fixture.r12domain.violation.ledgercore;

import com.paytech.arch.fixture.r12domain.DomainEventHandler;

@DomainEventHandler(eventType = "PaymentRefunded", strictOrder = false)
public class RefundPostingHandler {
}
