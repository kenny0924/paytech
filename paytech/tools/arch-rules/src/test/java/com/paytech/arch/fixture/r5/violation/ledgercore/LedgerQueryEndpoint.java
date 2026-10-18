package com.paytech.arch.fixture.r5.violation.ledgercore;

import org.springframework.web.bind.annotation.GetMapping;

public class LedgerQueryEndpoint {

    @GetMapping("/ledger/v1/entries")
    public String entries() {
        return "[]";
    }
}
