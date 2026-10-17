package com.paytech.arch.fixture.r12.pass.ledgercore;

import com.paytech.arch.fixture.r12.CompensationEventHandler;

public class ProjectionRebuildHandler implements CompensationEventHandler {

    @Override
    public String eventType() {
        return "LEDGER_PROJECTION_REBUILD";
    }

    @Override
    public void handle(String eventKey) {
    }
}
