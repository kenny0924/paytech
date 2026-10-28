package com.paytech.arch.fixture.r7.refdata.ledgercore;

import org.springframework.transaction.annotation.Transactional;

import com.paytech.arch.fixture.r7.refdata.referencedataclient.ReferenceDataRefresher;

public class LedgerRefreshService {

    private final ReferenceDataRefresher referenceDataRefresher;

    public LedgerRefreshService(ReferenceDataRefresher referenceDataRefresher) {
        this.referenceDataRefresher = referenceDataRefresher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void refresh() {
        referenceDataRefresher.refreshNow();
    }
}
