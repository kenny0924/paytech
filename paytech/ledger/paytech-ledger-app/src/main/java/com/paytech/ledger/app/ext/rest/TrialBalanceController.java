package com.paytech.ledger.app.ext.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.TrialBalanceApi;
import com.paytech.ledger.api.response.AccountBalanceListResponseBody;
import com.paytech.ledger.api.response.TrialBalanceDetailResponseBody;
import com.paytech.ledger.api.response.TrialBalanceRunResponseBody;
import com.paytech.ledger.app.ext.rest.converter.TrialBalanceConverter;
import com.paytech.ledger.domainservice.service.TrialBalanceService;
import com.paytech.ledger.service.processor.RunTrialBalanceProcessor;
import com.paytech.ledger.service.processor.request.RunTrialBalanceRequest;

import lombok.AllArgsConstructor;

/** 试算平衡的 HTTP 协议适配；执行委托 Processor 并按操作人 Header 记一条 INFO 操作日志（不落库），查询直接委托 TrialBalanceService。 */
@RestController
@AllArgsConstructor
public class TrialBalanceController implements TrialBalanceApi {
    private static final Logger LOG = LoggerFactory.getLogger(TrialBalanceController.class);

    private final TrialBalanceService trialBalanceService;
    private final TrialBalanceConverter converter;
    private final RunTrialBalanceProcessor runTrialBalanceProcessor;

    /** 执行试算并返回 201。 */
    @Override
    public ResponseEntity<TrialBalanceRunResponseBody> runTrialBalance(String actorAccountId, String sessionId,
        String ledgerBookId, String periodId) {
        RunTrialBalanceRequest request = new RunTrialBalanceRequest();
        request.setLedgerBookId(Long.valueOf(ledgerBookId));
        request.setPeriodId(Long.valueOf(periodId));
        TrialBalanceRunResponseBody response = converter.toResponseBody(runTrialBalanceProcessor.process(request));
        LOG.info("财务试算命令 action={} actor={} session={} ledgerBook={} period={} runNo={} status={}",
            "RUN_TRIAL_BALANCE", actorAccountId, sessionId, ledgerBookId, periodId, response.getRunNo(),
            response.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 分页查询试算。 */
    @Override
    public PagingResponseBody<TrialBalanceRunResponseBody> listTrialBalances(String ledgerBookId, String periodId,
        Integer page, Integer size) {
        return trialBalanceService.pageRuns(Long.parseLong(ledgerBookId), Long.parseLong(periodId),
            PagingRequestBody.of(page, size)).map(converter::toResponseBody);
    }

    /** 查询试算详情。 */
    @Override
    public TrialBalanceDetailResponseBody getTrialBalance(String ledgerBookId, String periodId, String runNo) {
        return converter.toResponseBody(trialBalanceService.getRun(Long.parseLong(ledgerBookId),
            Long.parseLong(periodId), Integer.parseInt(runNo)));
    }

    /** 实时查询科目余额（末级 + 级次汇总）。 */
    @Override
    public AccountBalanceListResponseBody listAccountBalances(String ledgerBookId, String periodId) {
        return converter.toResponseBody(trialBalanceService.listAccountBalances(Long.parseLong(ledgerBookId),
            Long.parseLong(periodId)));
    }
}
