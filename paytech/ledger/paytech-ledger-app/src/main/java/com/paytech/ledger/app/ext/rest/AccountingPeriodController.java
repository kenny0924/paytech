package com.paytech.ledger.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.AccountingPeriodCommandApi;
import com.paytech.ledger.api.response.OpenAccountingPeriodResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerSetupConverter;
import com.paytech.ledger.service.processor.OpenAccountingPeriodProcessor;
import com.paytech.ledger.service.processor.request.OpenAccountingPeriodRequest;

import lombok.AllArgsConstructor;

/** 会计期间状态流转的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class AccountingPeriodController implements AccountingPeriodCommandApi {
    private final LedgerSetupConverter converter;
    private final OpenAccountingPeriodProcessor openAccountingPeriodProcessor;

    /** 开放会计期间。 */
    @Override
    public OpenAccountingPeriodResponseBody openAccountingPeriod(String accountingPeriodId) {
        OpenAccountingPeriodRequest request = new OpenAccountingPeriodRequest();
        request.setAccountingPeriodId(Long.valueOf(accountingPeriodId));
        return converter.toResponseBody(openAccountingPeriodProcessor.process(request));
    }
}
