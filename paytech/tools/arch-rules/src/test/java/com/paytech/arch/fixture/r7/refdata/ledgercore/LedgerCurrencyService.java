package com.paytech.arch.fixture.r7.refdata.ledgercore;

import org.springframework.transaction.annotation.Transactional;

import com.paytech.arch.fixture.r7.refdata.referencedataclient.ReferenceCatalog;
import com.paytech.arch.fixture.r7.refdata.referencedataclient.ReferenceData;

public class LedgerCurrencyService {

    private final ReferenceData referenceData;

    private final ReferenceCatalog currencies;

    public LedgerCurrencyService(ReferenceData referenceData, ReferenceCatalog currencies) {
        this.referenceData = referenceData;
        this.currencies = currencies;
    }

    @Transactional(rollbackFor = Exception.class)
    public String post(String currency) {
        return referenceData.currencies().require(currency) + currencies.require(currency);
    }
}
