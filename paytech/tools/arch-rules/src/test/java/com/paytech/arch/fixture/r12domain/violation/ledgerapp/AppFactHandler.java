package com.paytech.arch.fixture.r12domain.violation.ledgerapp;

import com.paytech.arch.fixture.r12domain.DomainEventHandler;

@DomainEventHandler(eventType = "BillingFactRecorded", strictOrder = false)
public class AppFactHandler {
}
