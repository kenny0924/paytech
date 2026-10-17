package com.paytech.arch.fixture.r7.pass.ledgercore;

import org.springframework.transaction.annotation.Transactional;

public class LedgerPostingService {

    private final JournalRepository journalRepository;

    public LedgerPostingService(JournalRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void post(String entry) {
        journalRepository.save(entry);
    }
}
