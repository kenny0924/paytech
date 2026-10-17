package com.paytech.arch.fixture.r1.samesvc.postingcore;

import com.paytech.arch.fixture.r1.samesvc.journalcore.JournalWriter;

public class LedgerPostingService {

    private final JournalWriter journalWriter;

    public LedgerPostingService(JournalWriter journalWriter) {
        this.journalWriter = journalWriter;
    }
}
