package com.paytech.arch.fixture.r12;

public interface CompensationEventHandler {

    String eventType();

    void handle(String eventKey);
}
