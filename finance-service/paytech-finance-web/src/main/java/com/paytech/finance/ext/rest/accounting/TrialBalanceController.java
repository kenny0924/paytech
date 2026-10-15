package com.paytech.finance.ext.rest.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.domainservice.service.TrialBalanceService;
import com.paytech.finance.accounting.service.processor.RunTrialBalanceProcessor;
import com.paytech.finance.accounting.service.processor.request.RunTrialBalanceRequest;
import com.paytech.finance.api.accounting.TrialBalanceApi;
import com.paytech.finance.api.accounting.response.TrialBalanceDetailResponseBody;
import com.paytech.finance.api.accounting.response.TrialBalanceRunResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.TrialBalanceConverter;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 试算平衡的 HTTP 协议适配；执行委托 Processor，查询直接委托 TrialBalanceService。 */
@RestController
@AllArgsConstructor
public class TrialBalanceController implements TrialBalanceApi {
    private final TrialBalanceService trialBalanceService;
    private final TrialBalanceConverter converter;
    private final RunTrialBalanceProcessor runTrialBalanceProcessor;

    /** 执行试算并返回 201。 */
    @Override
    public ResponseEntity<TrialBalanceRunResponseBody> runTrialBalance(String ledgerBookId, String periodId) {
        RunTrialBalanceRequest request = new RunTrialBalanceRequest();
        request.setLedgerBookId(Long.valueOf(ledgerBookId));
        request.setPeriodId(Long.valueOf(periodId));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(converter.toResponseBody(runTrialBalanceProcessor.process(request)));
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
}
