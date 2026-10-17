package com.paytech.arch.fixture.r12;

public enum LedgerEventType {

    PROJECTION_REBUILD("LEDGER_PROJECTION_REBUILD");

    private final String code;

    LedgerEventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
