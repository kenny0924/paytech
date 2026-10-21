package com.paytech.reconciliation.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.bank.BankMatchApi;
import com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody;
import com.paytech.reconciliation.api.bank.response.BankMatchListResponseBody;
import com.paytech.reconciliation.api.bank.response.BankMatchResponseBody;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.app.ext.rest.converter.BankReconciliationConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.service.processor.ConfirmBankMatchProcessor;
import com.paytech.reconciliation.service.processor.CreateBankMatchesProcessor;
import com.paytech.reconciliation.service.processor.RejectBankMatchProcessor;
import com.paytech.reconciliation.service.processor.request.BankMatchCommandRequest;
import lombok.AllArgsConstructor;

/** 银行匹配建议确认 / 拒绝、人工多选匹配与查询的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class BankMatchController implements BankMatchApi {
    private final BankReconciliationConverter converter;
    private final RejectBankMatchProcessor rejectBankMatchProcessor;
    private final ConfirmBankMatchProcessor confirmBankMatchProcessor;
    private final CreateBankMatchesProcessor createBankMatchesProcessor;
    private final BankReconciliationQueryService bankReconciliationQueryService;

    /** 分页查询匹配。 */
    @Override
    public PagingResponseBody<BankMatchResponseBody> listBankMatches(String bankStatementLineId,
        String expectedMovementId, String status, Integer page, Integer size) {
        return bankReconciliationQueryService.listBankMatches(ReconciliationWire.id(bankStatementLineId),
            ReconciliationWire.id(expectedMovementId), ReconciliationWire.parse(BankMatchStatus.class, status),
            PagingRequestBody.of(page, size))
            .map(match -> converter.toMatchBody(match, bankReconciliationQueryService.currencyOf(match)));
    }

    /** 确认建议。 */
    @Override
    public BankMatchResponseBody confirmBankMatch(String actorAccountId, String sessionId, String bankMatchId) {
        return converter.toMatchBody(confirmBankMatchProcessor.process(
            new BankMatchCommandRequest(Long.valueOf(actorAccountId), Long.valueOf(bankMatchId))));
    }

    /** 拒绝建议。 */
    @Override
    public BankMatchResponseBody rejectBankMatch(String actorAccountId, String sessionId, String bankMatchId) {
        return converter.toMatchBody(rejectBankMatchProcessor.process(
            new BankMatchCommandRequest(Long.valueOf(actorAccountId), Long.valueOf(bankMatchId))));
    }

    /** 人工多选匹配并返回 201。 */
    @Override
    public ResponseEntity<BankMatchListResponseBody> createBankMatches(String actorAccountId, String sessionId,
        CreateBankMatchesRequestBody body) {
        BankMatchListResponseBody response = converter.toMatchListBody(createBankMatchesProcessor.process(
            converter.toCreateMatchesRequest(actorAccountId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
