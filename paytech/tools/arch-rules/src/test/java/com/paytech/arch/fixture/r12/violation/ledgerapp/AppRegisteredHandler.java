package com.paytech.arch.fixture.r12.violation.ledgerapp;

import com.paytech.arch.fixture.r12.CompensationEventHandler;

public class AppRegisteredHandler implements CompensationEventHandler {

    @Override
    public String eventType() {
        return "LEDGER_PROJECTION_REBUILD";
    }

    @Override
    public void handle(String eventKey) {
    }
}
