package com.paytech.ledger.app.ext.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.FiscalCalendarCommandApi;
import com.paytech.ledger.api.request.CreateAccountingPeriodRequestBody;
import com.paytech.ledger.api.request.CreateFiscalCalendarRequestBody;
import com.paytech.ledger.api.response.CreateAccountingPeriodResponseBody;
import com.paytech.ledger.api.response.CreateFiscalCalendarResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerSetupConverter;
import com.paytech.ledger.service.processor.CreateAccountingPeriodProcessor;
import com.paytech.ledger.service.processor.CreateFiscalCalendarProcessor;

import lombok.AllArgsConstructor;

/** 财务日历与会计期间创建的 HTTP 协议适配；命令成功后按操作人 Header 记一条 INFO 操作日志（不落库）。 */
@RestController
@AllArgsConstructor
public class FiscalCalendarController implements FiscalCalendarCommandApi {
    private static final Logger LOG = LoggerFactory.getLogger(FiscalCalendarController.class);

    private final LedgerSetupConverter converter;
    private final CreateFiscalCalendarProcessor createFiscalCalendarProcessor;
    private final CreateAccountingPeriodProcessor createAccountingPeriodProcessor;

    /** 创建财务日历并返回 201。 */
    @Override
    public ResponseEntity<CreateFiscalCalendarResponseBody> createFiscalCalendar(String actorAccountId,
        String sessionId, CreateFiscalCalendarRequestBody body) {
        CreateFiscalCalendarResponseBody response = new CreateFiscalCalendarResponseBody();
        response.setFiscalCalendarId(createFiscalCalendarProcessor.process(converter.toRequest(body)).getId().toString());
        LOG.info("财务基础资料命令 action={} actor={} session={} resourceId={}", "CREATE_FISCAL_CALENDAR",
            actorAccountId, sessionId, response.getFiscalCalendarId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 创建会计期间并返回 201。 */
    @Override
    public ResponseEntity<CreateAccountingPeriodResponseBody> createAccountingPeriod(String actorAccountId,
        String sessionId, String fiscalCalendarId, CreateAccountingPeriodRequestBody body) {
        CreateAccountingPeriodResponseBody response = new CreateAccountingPeriodResponseBody();
        response.setAccountingPeriodId(
            createAccountingPeriodProcessor.process(converter.toRequest(fiscalCalendarId, body)).getId().toString());
        LOG.info("财务基础资料命令 action={} actor={} session={} resourceId={}", "CREATE_ACCOUNTING_PERIOD",
            actorAccountId, sessionId, response.getAccountingPeriodId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
