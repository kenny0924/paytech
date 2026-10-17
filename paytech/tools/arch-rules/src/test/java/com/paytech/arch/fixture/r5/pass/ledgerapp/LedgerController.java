package com.paytech.arch.fixture.r5.pass.ledgerapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgerController {

    @GetMapping("/ledger/v1/books")
    public String books() {
        return "[]";
    }
}
