package com.paytech.arch.fixture.r12domain.pass.ledgertest;

import com.paytech.arch.fixture.r12domain.DomainEventHandler;

@DomainEventHandler(eventType = "TestOnlyRecorded", strictOrder = false)
public class UnregisteredTestHandler {
}
