package com.paytech.reconciliation.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.bank.BankStatementApi;
import com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody;
import com.paytech.reconciliation.api.bank.request.StartBankStatementImportRequestBody;
import com.paytech.reconciliation.api.bank.response.BankStatementCompletionResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementLineDetailResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementLineResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementResponseBody;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.app.ext.rest.converter.BankReconciliationConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconExceptionConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.service.processor.AppendBankStatementLinesProcessor;
import com.paytech.reconciliation.service.processor.CompleteBankStatementImportProcessor;
import com.paytech.reconciliation.service.processor.StartBankStatementImportProcessor;
import com.paytech.reconciliation.service.processor.request.BankStatementCommandRequest;
import lombok.AllArgsConstructor;

/** 银行对账单分段导入与银行流水查询的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class BankStatementController implements BankStatementApi {
    private final BankReconciliationConverter converter;
    private final ReconExceptionConverter reconExceptionConverter;
    private final BankReconciliationQueryService bankReconciliationQueryService;
    private final StartBankStatementImportProcessor startBankStatementImportProcessor;
    private final AppendBankStatementLinesProcessor appendBankStatementLinesProcessor;
    private final CompleteBankStatementImportProcessor completeBankStatementImportProcessor;

    /** 开始导入并返回 201。 */
    @Override
    public ResponseEntity<BankStatementResponseBody> startBankStatementImport(String actorAccountId, String sessionId,
        StartBankStatementImportRequestBody body) {
        BankStatementResponseBody response = converter.toStatementBody(
            startBankStatementImportProcessor.process(converter.toStartRequest(actorAccountId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 追加一段流水。 */
    @Override
    public BankStatementResponseBody appendBankStatementLines(String actorAccountId, String sessionId,
        String bankStatementId, AppendBankStatementLinesRequestBody body) {
        return converter.toStatementBody(appendBankStatementLinesProcessor.process(
            converter.toAppendRequest(actorAccountId, bankStatementId, body)));
    }

    /** 完成导入。 */
    @Override
    public BankStatementCompletionResponseBody completeBankStatementImport(String actorAccountId, String sessionId,
        String bankStatementId) {
        return converter.toCompletionBody(completeBankStatementImportProcessor.process(
            new BankStatementCommandRequest(Long.valueOf(actorAccountId), Long.valueOf(bankStatementId))));
    }

    /** 分页查询对账单。 */
    @Override
    public PagingResponseBody<BankStatementResponseBody> listBankStatements(String cashAccountId, String status,
        Integer page, Integer size) {
        return bankReconciliationQueryService.listBankStatements(ReconciliationWire.id(cashAccountId),
            ReconciliationWire.parse(BankStatementStatus.class, status), PagingRequestBody.of(page, size))
            .map(converter::toStatementBody);
    }

    /** 对账单头（详情页）。 */
    @Override
    public BankStatementResponseBody getBankStatement(String bankStatementId) {
        return converter.toStatementBody(bankReconciliationQueryService.readBankStatement(Long.parseLong(bankStatementId)));
    }

    /** 银行流水详情（详情页）。 */
    @Override
    public BankStatementLineDetailResponseBody getBankStatementLine(String bankStatementLineId) {
        return converter.toLineDetailBody(
            bankReconciliationQueryService.readBankStatementLine(Long.parseLong(bankStatementLineId)),
            reconExceptionConverter::toExceptionBody);
    }

    /** 一张对账单的流水（行号升序）。 */
    @Override
    public PagingResponseBody<BankStatementLineResponseBody> listBankStatementLinesOfStatement(String bankStatementId,
        Integer page, Integer size) {
        long id = Long.parseLong(bankStatementId);
        String currency = bankReconciliationQueryService.getBankStatement(id).getCurrency();
        return bankReconciliationQueryService.listBankStatementLines(id, null, null, null, null, null,
            PagingRequestBody.of(page, size)).map(line -> converter.toLineBody(line, currency));
    }

    /** 跨对账单查询银行流水（起息日升序）。 */
    @Override
    public PagingResponseBody<BankStatementLineResponseBody> listBankStatementLines(String cashAccountId,
        String matchStatus, String direction, String valueDateFrom, String valueDateTo, Integer page, Integer size) {
        long accountId = Long.parseLong(cashAccountId);
        String currency = bankReconciliationQueryService.getCashAccount(accountId).getCurrency();
        return bankReconciliationQueryService.listBankStatementLines(null, accountId,
            ReconciliationWire.parse(LineMatchStatus.class, matchStatus),
            ReconciliationWire.parse(SettlementDirection.class, direction), ReconciliationWire.date(valueDateFrom),
            ReconciliationWire.date(valueDateTo), PagingRequestBody.of(page, size))
            .map(line -> converter.toLineBody(line, currency));
    }
}
