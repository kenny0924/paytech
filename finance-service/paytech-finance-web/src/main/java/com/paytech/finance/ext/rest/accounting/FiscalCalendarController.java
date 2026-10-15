package com.paytech.finance.ext.rest.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.service.processor.CreateAccountingPeriodProcessor;
import com.paytech.finance.accounting.service.processor.CreateFiscalCalendarProcessor;
import com.paytech.finance.api.accounting.FiscalCalendarCommandApi;
import com.paytech.finance.api.accounting.request.CreateAccountingPeriodRequestBody;
import com.paytech.finance.api.accounting.request.CreateFiscalCalendarRequestBody;
import com.paytech.finance.api.accounting.response.CreateAccountingPeriodResponseBody;
import com.paytech.finance.api.accounting.response.CreateFiscalCalendarResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.LedgerSetupConverter;

import lombok.AllArgsConstructor;

/** 财务日历与会计期间创建的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class FiscalCalendarController implements FiscalCalendarCommandApi {
    private final LedgerSetupConverter converter;
    private final CreateFiscalCalendarProcessor createFiscalCalendarProcessor;
    private final CreateAccountingPeriodProcessor createAccountingPeriodProcessor;

    /** 创建财务日历并返回 201。 */
    @Override
    public ResponseEntity<CreateFiscalCalendarResponseBody> createFiscalCalendar(CreateFiscalCalendarRequestBody body) {
        CreateFiscalCalendarResponseBody response = new CreateFiscalCalendarResponseBody();
        response.setFiscalCalendarId(createFiscalCalendarProcessor.process(converter.toRequest(body)).getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 创建会计期间并返回 201。 */
    @Override
    public ResponseEntity<CreateAccountingPeriodResponseBody> createAccountingPeriod(String fiscalCalendarId,
        CreateAccountingPeriodRequestBody body) {
        CreateAccountingPeriodResponseBody response = new CreateAccountingPeriodResponseBody();
        response.setAccountingPeriodId(
            createAccountingPeriodProcessor.process(converter.toRequest(fiscalCalendarId, body)).getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
