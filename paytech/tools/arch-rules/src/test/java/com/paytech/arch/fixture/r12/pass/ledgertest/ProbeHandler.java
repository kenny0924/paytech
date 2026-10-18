package com.paytech.arch.fixture.r12.pass.ledgertest;

import com.paytech.arch.fixture.r12.CompensationEventHandler;

public class ProbeHandler implements CompensationEventHandler {

    @Override
    public String eventType() {
        return "TEST_ONLY_PROBE";
    }

    @Override
    public void handle(String eventKey) {
    }
}
