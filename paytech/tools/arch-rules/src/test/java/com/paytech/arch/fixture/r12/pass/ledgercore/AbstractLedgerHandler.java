package com.paytech.arch.fixture.r12.pass.ledgercore;

import com.paytech.arch.fixture.r12.CompensationEventHandler;
import com.paytech.arch.fixture.r12.LedgerEventTypes;

public abstract class AbstractLedgerHandler implements CompensationEventHandler {

    @Override
    public String eventType() {
        return LedgerEventTypes.PERIOD_CLOSE;
    }
}
