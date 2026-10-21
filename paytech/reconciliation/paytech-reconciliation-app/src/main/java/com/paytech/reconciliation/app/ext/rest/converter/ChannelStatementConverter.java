package com.paytech.reconciliation.app.ext.rest.converter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody;
import com.paytech.reconciliation.api.statement.request.StartChannelStatementImportRequestBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementCompletionResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementDetailResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementLineResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementResponseBody;
import com.paytech.reconciliation.api.statement.response.PlatformTxnResponseBody;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.ReconcileGate;
import com.paytech.reconciliation.domain.view.ChannelStatementDetailView;
import com.paytech.reconciliation.domain.view.ChannelStatementView;
import com.paytech.reconciliation.service.processor.request.AppendChannelStatementLinesRequest;
import com.paytech.reconciliation.service.processor.request.StartChannelStatementImportRequest;
import com.paytech.reconciliation.service.processor.response.CompleteChannelStatementResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 渠道账单与平台流水 HTTP 协议转换。 */
@Component
public class ChannelStatementConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 开始导入命令。 */
    public StartChannelStatementImportRequest toStartRequest(String actorAccountId,
        StartChannelStatementImportRequestBody body) {
        return StartChannelStatementImportRequest.builder().actorId(Long.valueOf(actorAccountId))
            .clearingAccountCode(body.getClearingAccountCode()).channelBatchRef(body.getChannelBatchRef())
            .settlementDate(body.getSettlementDate()).currency(body.getCurrency())
            .grossAmount(ReconciliationWire.decimal(body.getGrossAmount()))
            .feeAmount(ReconciliationWire.decimal(body.getFeeAmount()))
            .netAmount(ReconciliationWire.decimal(body.getNetAmount()))
            .cashDirection(ReconciliationWire.parse(CashDirection.class, body.getCashDirection()))
            .fileName(body.getFileName()).fileContentHash(body.getFileContentHash())
            .declaredLineCount(body.getDeclaredLineCount()).build();
    }

    /** 追加明细命令。 */
    public AppendChannelStatementLinesRequest toAppendRequest(String actorAccountId, String statementId,
        AppendChannelStatementLinesRequestBody body) {
        List<AppendChannelStatementLinesRequest.Line> lines = body.getLines().stream()
            .map(line -> AppendChannelStatementLinesRequest.Line.builder().lineNo(line.getLineNo())
                .lineType(ReconciliationWire.parse(ChannelLineType.class, line.getLineType()))
                .direction(ReconciliationWire.parse(SettlementDirection.class, line.getDirection()))
                .amount(ReconciliationWire.decimal(line.getAmount()))
                .feeAmount(ReconciliationWire.decimal(line.getFeeAmount())).referenceType(line.getReferenceType())
                .externalTransactionId(line.getExternalTransactionId()).transactionDate(line.getTransactionDate())
                .rawAttributes(line.getRawAttributes()).build())
            .toList();
        return new AppendChannelStatementLinesRequest(Long.valueOf(actorAccountId), Long.valueOf(statementId), lines);
    }

    /** 账单响应。 */
    public ChannelStatementResponseBody toResponseBody(ChannelStatementView view) {
        ChannelStatement statement = view.getStatement();
        String currency = statement.getCurrency();
        ChannelStatementResponseBody body = new ChannelStatementResponseBody();
        body.setStatementId(statement.getId().toString());
        body.setClearingAccountId(statement.getClearingAccountId().toString());
        body.setClearingAccountCode(view.getClearingAccount().getClearingAccountCode());
        body.setChannelBatchRef(statement.getChannelBatchRef());
        body.setRevision(statement.getRevision());
        body.setSettlementDate(statement.getSettlementDate());
        body.setCurrency(currency);
        body.setGrossAmount(ReconciliationWire.amount(currency, statement.getGrossAmount()));
        body.setFeeAmount(ReconciliationWire.amount(currency, statement.getFeeAmount()));
        body.setNetAmount(ReconciliationWire.amount(currency, statement.getNetAmount()));
        body.setCashDirection(statement.getCashDirection().getCode());
        body.setDeclaredLineCount(statement.getDeclaredLineCount());
        body.setImportedLineCount(statement.getImportedLineCount());
        body.setFileName(statement.getFileName());
        body.setFileContentHash(statement.getFileContentHash());
        body.setRejectReason(statement.getRejectReason());
        body.setStatus(statement.getStatus().getCode());
        body.setCashConfirmedAmount(ReconciliationWire.amount(currency, statement.getCashConfirmedAmount()));
        body.setReconciledTime(statement.getReconciledTime());
        body.setMakerId(statement.getMakerId().toString());
        body.setVersion(statement.getVersion().toString());
        body.setCreateTime(statement.getCreateTime());
        body.setUpdateTime(statement.getUpdateTime());
        return body;
    }

    /** 完成导入结果。 */
    public ChannelStatementCompletionResponseBody toCompletionResponseBody(CompleteChannelStatementResponse response) {
        ChannelStatement statement = response.getStatement();
        ChannelStatementCompletionResponseBody body = new ChannelStatementCompletionResponseBody();
        body.setStatementId(statement.getId().toString());
        body.setStatus(statement.getStatus().getCode());
        body.setImportedLineCount(statement.getImportedLineCount());
        body.setRejectReason(statement.getRejectReason());
        body.setRejections(response.getRejections().stream().map(rejection -> {
            ChannelStatementCompletionResponseBody.LineRejection item =
                new ChannelStatementCompletionResponseBody.LineRejection();
            item.setLineNo(rejection.getLineNo());
            item.setReason(rejection.getReason());
            return item;
        }).toList());
        return body;
    }

    /** 账单详情。 */
    public ChannelStatementDetailResponseBody toDetailResponseBody(ChannelStatementDetailView view) {
        ChannelStatementDetailResponseBody body = new ChannelStatementDetailResponseBody();
        body.setStatement(toResponseBody(new ChannelStatementView(view.getStatement(), view.getClearingAccount())));
        body.setUnmatchedLineCount((int)view.getUnmatchedLineCount());
        body.setMatchedLineCount((int)view.getMatchedLineCount());
        body.setExceptionLineCount((int)view.getExceptionLineCount());
        body.setOpenExceptionCount((int)view.getOpenExceptionCount());
        body.setExpectedMovementId(ReconciliationWire.text(view.getExpectedMovementId()));
        body.setGate(view.getGate() == null ? null : toGate(view.getGate()));
        return body;
    }

    /** 账单行。 */
    public ChannelStatementLineResponseBody toLineResponseBody(ChannelStatementLine line, String currency) {
        ChannelStatementLineResponseBody body = new ChannelStatementLineResponseBody();
        body.setLineId(line.getId().toString());
        body.setStatementId(line.getStatementId().toString());
        body.setLineNo(line.getLineNo());
        body.setLineType(line.getLineType().getCode());
        body.setDirection(line.getDirection().getCode());
        body.setAmount(ReconciliationWire.amount(currency, line.getAmount()));
        body.setFeeAmount(ReconciliationWire.amount(currency, line.getFeeAmount()));
        body.setReferenceType(line.getReferenceType());
        body.setExternalTransactionId(line.getExternalTransactionId());
        body.setTransactionDate(line.getTransactionDate());
        body.setRawAttributes(line.getRawAttributes() == null ? null
            : JSON.readValue(line.getRawAttributes(), new TypeReference<Map<String, String>>() {}));
        body.setPlatformTxnId(ReconciliationWire.text(line.getPlatformTxnId()));
        body.setMatchMethod(ReconciliationWire.code(line.getMatchMethod()));
        body.setMatchedTime(line.getMatchedTime());
        body.setActualFee(ReconciliationWire.amount(currency, line.getActualFee()));
        body.setResolutionId(ReconciliationWire.text(line.getResolutionId()));
        body.setMatchStatus(line.getMatchStatus().getCode());
        return body;
    }

    /** 平台流水。 */
    public PlatformTxnResponseBody toPlatformTxnResponseBody(PlatformTxn txn) {
        String currency = txn.getCurrency();
        PlatformTxnResponseBody body = new PlatformTxnResponseBody();
        body.setPlatformTxnId(txn.getId().toString());
        body.setClearingAccountId(txn.getClearingAccountId().toString());
        body.setDirection(txn.getDirection().getCode());
        body.setTxnSource(txn.getTxnSource().getCode());
        body.setBillingFactId(txn.getBillingFactId().toString());
        body.setEntityNo(txn.getEntityNo());
        body.setSourceRefType(txn.getSourceRefType().getCode());
        body.setSourceRefId(txn.getSourceRefId());
        body.setChannelTransactionRefId(txn.getChannelTransactionRefId());
        body.setReferenceType(txn.getReferenceType());
        body.setExternalTransactionId(txn.getExternalTransactionId());
        body.setCurrency(currency);
        body.setExpectedAmount(ReconciliationWire.amount(currency, txn.getExpectedAmount()));
        body.setExpectedFee(ReconciliationWire.amount(currency, txn.getExpectedFee()));
        body.setMatchedAmount(ReconciliationWire.amount(currency, txn.getMatchedAmount()));
        body.setActualFee(ReconciliationWire.amount(currency, txn.getActualFee()));
        body.setSettledAmount(ReconciliationWire.amount(currency, txn.getSettledAmount()));
        body.setStatus(txn.getStatus().getCode());
        body.setBusinessTime(txn.getBusinessTime());
        body.setVersion(txn.getVersion().toString());
        body.setCreateTime(txn.getCreateTime());
        body.setUpdateTime(txn.getUpdateTime());
        return body;
    }

    /** 对平门禁。 */
    static ChannelStatementDetailResponseBody.ReconcileGate toGate(ReconcileGate gate) {
        ChannelStatementDetailResponseBody.ReconcileGate body = new ChannelStatementDetailResponseBody.ReconcileGate();
        body.setTransactionLinesMatched(gate.isTransactionLinesMatched());
        body.setAdjustmentLinesClassified(gate.isAdjustmentLinesClassified());
        body.setNoBlockingException(gate.isNoBlockingException());
        body.setTotalsConsistent(gate.isTotalsConsistent());
        body.setSettlementCashAccountConfigured(gate.isSettlementCashAccountConfigured());
        return body;
    }
}
