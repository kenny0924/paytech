package com.paytech.arch.fixture.r8.pass.messaging;

import com.paytech.arch.fixture.r8.pass.messagingapi.EventPublisher;

public class RabbitEventPublisher implements EventPublisher {

    @Override
    public void publish(String eventType, String payload) {
    }
}
