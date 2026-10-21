package com.paytech.reconciliation.app.ext.rest.converter;

import java.util.List;
import org.springframework.stereotype.Component;
import com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody;
import com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody;
import com.paytech.reconciliation.api.bank.request.StartBankStatementImportRequestBody;
import com.paytech.reconciliation.api.bank.response.BankMatchListResponseBody;
import com.paytech.reconciliation.api.bank.response.BankMatchResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementCompletionResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementLineResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementResponseBody;
import com.paytech.reconciliation.api.bank.response.ExpectedBankMovementResponseBody;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.service.processor.request.AppendBankStatementLinesRequest;
import com.paytech.reconciliation.service.processor.request.CreateBankMatchesRequest;
import com.paytech.reconciliation.service.processor.request.StartBankStatementImportRequest;
import com.paytech.reconciliation.service.processor.response.BankMatchResponse;
import com.paytech.reconciliation.service.processor.response.CompleteBankStatementResponse;

/** 银行对账单、银行流水、预期银行流水与银行匹配的 HTTP 协议转换。 */
@Component
public class BankReconciliationConverter {

    /** 开始导入命令。 */
    public StartBankStatementImportRequest toStartRequest(String actorAccountId,
        StartBankStatementImportRequestBody body) {
        return StartBankStatementImportRequest.builder().actorId(Long.valueOf(actorAccountId))
            .cashAccountId(Long.valueOf(body.getCashAccountId())).bankAccountLast4(body.getBankAccountLast4())
            .statementRef(body.getStatementRef()).periodStart(body.getPeriodStart()).periodEnd(body.getPeriodEnd())
            .currency(body.getCurrency()).openingBalance(ReconciliationWire.decimal(body.getOpeningBalance()))
            .closingBalance(ReconciliationWire.decimal(body.getClosingBalance())).fileName(body.getFileName())
            .fileContentHash(body.getFileContentHash()).declaredLineCount(body.getDeclaredLineCount()).build();
    }

    /** 追加流水命令。 */
    public AppendBankStatementLinesRequest toAppendRequest(String actorAccountId, String statementId,
        AppendBankStatementLinesRequestBody body) {
        List<AppendBankStatementLinesRequest.Line> lines = body.getLines().stream()
            .map(line -> AppendBankStatementLinesRequest.Line.builder().lineNo(line.getLineNo())
                .bankTransactionRef(line.getBankTransactionRef()).valueDate(line.getValueDate())
                .direction(ReconciliationWire.parse(SettlementDirection.class, line.getDirection()))
                .amount(ReconciliationWire.decimal(line.getAmount())).counterpartyName(line.getCounterpartyName())
                .referenceText(line.getReferenceText()).build())
            .toList();
        return new AppendBankStatementLinesRequest(Long.valueOf(actorAccountId), Long.valueOf(statementId), lines);
    }

    /** 人工多选匹配命令。 */
    public CreateBankMatchesRequest toCreateMatchesRequest(String actorAccountId, CreateBankMatchesRequestBody body) {
        List<CreateBankMatchesRequest.Allocation> allocations = body.getAllocations().stream()
            .map(allocation -> new CreateBankMatchesRequest.Allocation(Long.valueOf(allocation.getExpectedMovementId()),
                ReconciliationWire.decimal(allocation.getAmount()))).toList();
        return new CreateBankMatchesRequest(Long.valueOf(actorAccountId), Long.valueOf(body.getBankStatementLineId()),
            allocations);
    }

    /** 对账单响应。 */
    public BankStatementResponseBody toStatementBody(BankStatement statement) {
        String currency = statement.getCurrency();
        BankStatementResponseBody body = new BankStatementResponseBody();
        body.setBankStatementId(statement.getId().toString());
        body.setCashAccountId(statement.getCashAccountId().toString());
        body.setStatementRef(statement.getStatementRef());
        body.setPeriodStart(statement.getPeriodStart());
        body.setPeriodEnd(statement.getPeriodEnd());
        body.setCurrency(currency);
        body.setOpeningBalance(ReconciliationWire.amount(currency, statement.getOpeningBalance()));
        body.setClosingBalance(ReconciliationWire.amount(currency, statement.getClosingBalance()));
        body.setDeclaredLineCount(statement.getDeclaredLineCount());
        body.setImportedLineCount(statement.getImportedLineCount());
        body.setDuplicateLineCount(statement.getDuplicateLineCount());
        body.setFileName(statement.getFileName());
        body.setFileContentHash(statement.getFileContentHash());
        body.setRejectReason(rejectReason(statement));
        body.setStatus(statement.getStatus().getCode());
        body.setMakerId(statement.getMakerId().toString());
        body.setCreateTime(statement.getCreateTime());
        body.setUpdateTime(statement.getUpdateTime());
        return body;
    }

    /** 完成导入响应。 */
    public BankStatementCompletionResponseBody toCompletionBody(CompleteBankStatementResponse response) {
        BankStatement statement = response.getStatement();
        BankStatementCompletionResponseBody body = new BankStatementCompletionResponseBody();
        body.setBankStatementId(statement.getId().toString());
        body.setStatus(statement.getStatus().getCode());
        body.setImportedLineCount(statement.getImportedLineCount());
        body.setDuplicateLineCount(statement.getDuplicateLineCount());
        body.setGapExceptionId(ReconciliationWire.text(response.getGapExceptionId()));
        body.setRejectReason(rejectReason(statement));
        body.setRejections(response.getRejections().stream().map(rejection -> {
            BankStatementCompletionResponseBody.LineRejection item = new BankStatementCompletionResponseBody.LineRejection();
            item.setLineNo(rejection.getLineNo());
            item.setReason(rejection.getReason());
            return item;
        }).toList());
        return body;
    }

    /** 整单拒绝原因：只在 REJECTED 时返回（接收中该列暂存逐行重复原因，不外显）。 */
    static String rejectReason(BankStatement statement) {
        return statement.getStatus() == BankStatementStatus.REJECTED ? statement.getRejectReason() : null;
    }

    /** 银行流水响应。 */
    public BankStatementLineResponseBody toLineBody(BankStatementLine line, String currency) {
        BankStatementLineResponseBody body = new BankStatementLineResponseBody();
        body.setBankStatementLineId(line.getId().toString());
        body.setBankStatementId(line.getStatementId().toString());
        body.setCashAccountId(line.getCashAccountId().toString());
        body.setLineNo(line.getLineNo());
        body.setBankTransactionRef(line.getBankTransactionRef());
        body.setValueDate(line.getValueDate());
        body.setDirection(line.getDirection().getCode());
        body.setAmount(ReconciliationWire.amount(currency, line.getAmount()));
        body.setCurrency(currency);
        body.setCounterpartyName(line.getCounterpartyName());
        body.setReferenceText(line.getReferenceText());
        body.setMatchedAmount(ReconciliationWire.amount(currency, line.getMatchedAmount()));
        body.setMatchStatus(line.getMatchStatus().getCode());
        body.setVersion(line.getVersion().toString());
        return body;
    }

    /** 预期银行流水响应。 */
    public ExpectedBankMovementResponseBody toMovementBody(ExpectedBankMovement movement) {
        String currency = movement.getCurrency();
        ExpectedBankMovementResponseBody body = new ExpectedBankMovementResponseBody();
        body.setExpectedMovementId(movement.getId().toString());
        body.setMovementType(movement.getMovementType().getCode());
        body.setSourceId(movement.getSourceId().toString());
        body.setCashAccountId(movement.getCashAccountId().toString());
        body.setEntityNo(movement.getEntityNo());
        body.setDirection(movement.getDirection().getCode());
        body.setCurrency(currency);
        body.setExpectedAmount(ReconciliationWire.amount(currency, movement.getExpectedAmount()));
        body.setExpectedDate(movement.getExpectedDate());
        body.setBankPaymentRef(movement.getBankPaymentRef());
        body.setConfirmedAmount(ReconciliationWire.amount(currency, movement.getConfirmedAmount()));
        body.setClassifiedAmount(ReconciliationWire.amount(currency, movement.getClassifiedAmount()));
        body.setStatus(movement.getStatus().getCode());
        body.setVersion(movement.getVersion().toString());
        body.setCreateTime(movement.getCreateTime());
        body.setUpdateTime(movement.getUpdateTime());
        return body;
    }

    /** 单条匹配响应。 */
    public BankMatchResponseBody toMatchBody(BankMatch match, String currency) {
        BankMatchResponseBody body = new BankMatchResponseBody();
        body.setBankMatchId(match.getId().toString());
        body.setBankStatementLineId(match.getBankStatementLineId().toString());
        body.setExpectedMovementId(match.getExpectedMovementId().toString());
        body.setMatchedAmount(ReconciliationWire.amount(currency, match.getMatchedAmount()));
        body.setMatchMethod(match.getMatchMethod().getCode());
        body.setPayoutComponentType(ReconciliationWire.code(match.getPayoutComponentType()));
        body.setStatus(match.getStatus().getCode());
        body.setResolutionId(ReconciliationWire.text(match.getResolutionId()));
        body.setOperatorId(ReconciliationWire.text(match.getOperatorId()));
        body.setDecisionTime(match.getDecisionTime());
        body.setCreateTime(match.getCreateTime());
        return body;
    }

    /** 确认 / 拒绝命令的单条结果。 */
    public BankMatchResponseBody toMatchBody(BankMatchResponse response) {
        return toMatchBody(response.getMatches().getFirst(), response.getCurrency());
    }

    /** 人工多选匹配结果。 */
    public BankMatchListResponseBody toMatchListBody(BankMatchResponse response) {
        BankMatchListResponseBody body = new BankMatchListResponseBody();
        body.setItems(response.getMatches().stream().map(match -> toMatchBody(match, response.getCurrency())).toList());
        return body;
    }
}
