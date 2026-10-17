package com.paytech.arch.fixture.r12domain.violation.ledgercore;

import com.paytech.arch.fixture.r12domain.DomainEventHandler;

@DomainEventHandler(eventType = "BalanceMoved", strictOrder = true)
public class BalanceEchoHandler {
}
