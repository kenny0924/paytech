package com.paytech.ledger.app.ext.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.AccountingPeriodCommandApi;
import com.paytech.ledger.api.response.OpenAccountingPeriodResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerSetupConverter;
import com.paytech.ledger.service.processor.OpenAccountingPeriodProcessor;
import com.paytech.ledger.service.processor.request.OpenAccountingPeriodRequest;

import lombok.AllArgsConstructor;

/** 会计期间状态流转的 HTTP 协议适配；命令成功后按操作人 Header 记一条 INFO 操作日志（不落库）。 */
@RestController
@AllArgsConstructor
public class AccountingPeriodController implements AccountingPeriodCommandApi {
    private static final Logger LOG = LoggerFactory.getLogger(AccountingPeriodController.class);

    private final LedgerSetupConverter converter;
    private final OpenAccountingPeriodProcessor openAccountingPeriodProcessor;

    /** 开放会计期间。 */
    @Override
    public OpenAccountingPeriodResponseBody openAccountingPeriod(String actorAccountId, String sessionId,
        String accountingPeriodId) {
        OpenAccountingPeriodRequest request = new OpenAccountingPeriodRequest();
        request.setAccountingPeriodId(Long.valueOf(accountingPeriodId));
        OpenAccountingPeriodResponseBody response =
            converter.toResponseBody(openAccountingPeriodProcessor.process(request));
        LOG.info("财务基础资料命令 action={} actor={} session={} resourceId={}", "OPEN_ACCOUNTING_PERIOD",
            actorAccountId, sessionId, accountingPeriodId);
        return response;
    }
}
