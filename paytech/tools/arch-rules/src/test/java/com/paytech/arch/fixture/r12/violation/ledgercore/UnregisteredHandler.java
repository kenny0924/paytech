package com.paytech.arch.fixture.r12.violation.ledgercore;

import com.paytech.arch.fixture.r12.CompensationEventHandler;

public class UnregisteredHandler implements CompensationEventHandler {

    @Override
    public String eventType() {
        return "LEDGER_UNREGISTERED";
    }

    @Override
    public void handle(String eventKey) {
    }
}
