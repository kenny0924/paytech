package com.paytech.arch.fixture.r8.pass.ledgerapp;

import com.paytech.arch.fixture.r8.pass.messaging.RabbitEventPublisher;
import com.paytech.arch.fixture.r8.pass.messagingapi.EventPublisher;

public class LedgerMessagingConfiguration {

    public EventPublisher eventPublisher() {
        return new RabbitEventPublisher();
    }
}
