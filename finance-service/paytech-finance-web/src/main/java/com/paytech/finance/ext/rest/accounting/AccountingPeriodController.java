package com.paytech.finance.ext.rest.accounting;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.service.processor.OpenAccountingPeriodProcessor;
import com.paytech.finance.accounting.service.processor.request.OpenAccountingPeriodRequest;
import com.paytech.finance.api.accounting.AccountingPeriodCommandApi;
import com.paytech.finance.api.accounting.response.OpenAccountingPeriodResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.LedgerSetupConverter;

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
