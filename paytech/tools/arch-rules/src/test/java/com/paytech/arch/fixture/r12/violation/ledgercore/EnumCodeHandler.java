package com.paytech.arch.fixture.r12.violation.ledgercore;

import com.paytech.arch.fixture.r12.CompensationEventHandler;
import com.paytech.arch.fixture.r12.LedgerEventType;

public class EnumCodeHandler implements CompensationEventHandler {

    @Override
    public String eventType() {
        return LedgerEventType.PROJECTION_REBUILD.getCode();
    }

    @Override
    public void handle(String eventKey) {
    }
}
