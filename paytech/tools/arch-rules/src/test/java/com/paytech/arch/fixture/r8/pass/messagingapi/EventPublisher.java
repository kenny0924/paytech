package com.paytech.arch.fixture.r8.pass.messagingapi;

public interface EventPublisher {

    void publish(String eventType, String payload);
}
