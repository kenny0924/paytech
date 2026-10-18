package com.paytech.arch.fixture.r7.violation.ledgercore;

import org.springframework.transaction.annotation.Transactional;
import com.paytech.arch.fixture.r7.violation.vaultclient.VaultClient;

public class LedgerRevealService {

    private VaultClient vaultClient;

    private ExchangeRateClient exchangeRateClient;

    @Transactional
    public void reveal() {
    }
}
