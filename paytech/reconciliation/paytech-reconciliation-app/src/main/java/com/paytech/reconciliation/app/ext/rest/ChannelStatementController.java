package com.paytech.reconciliation.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.statement.ChannelStatementApi;
import com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody;
import com.paytech.reconciliation.api.statement.request.StartChannelStatementImportRequestBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementCompletionResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementDetailResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementLineResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ChannelStatementConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domain.view.ChannelStatementDetailView;
import com.paytech.reconciliation.domainservice.service.ChannelStatementQueryService;
import com.paytech.reconciliation.service.processor.AppendChannelStatementLinesProcessor;
import com.paytech.reconciliation.service.processor.CompleteChannelStatementImportProcessor;
import com.paytech.reconciliation.service.processor.StartChannelStatementImportProcessor;
import com.paytech.reconciliation.service.processor.VoidChannelStatementProcessor;
import com.paytech.reconciliation.service.processor.request.ChannelStatementCommandRequest;

import lombok.AllArgsConstructor;

/** 渠道账单分段导入、作废与查询的 HTTP 协议适配：写入口委托 Processor，查询委托查询 Service。 */
@RestController
@AllArgsConstructor
public class ChannelStatementController implements ChannelStatementApi {
    private final ChannelStatementConverter converter;
    private final ChannelStatementQueryService channelStatementQueryService;
    private final VoidChannelStatementProcessor voidChannelStatementProcessor;
    private final StartChannelStatementImportProcessor startChannelStatementImportProcessor;
    private final AppendChannelStatementLinesProcessor appendChannelStatementLinesProcessor;
    private final CompleteChannelStatementImportProcessor completeChannelStatementImportProcessor;

    /** 开始导入并返回 201。 */
    @Override
    public ResponseEntity<ChannelStatementResponseBody> startChannelStatementImport(String actorAccountId,
        String sessionId, StartChannelStatementImportRequestBody body) {
        ChannelStatementResponseBody response = converter.toResponseBody(
            startChannelStatementImportProcessor.process(converter.toStartRequest(actorAccountId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 追加一段明细。 */
    @Override
    public ChannelStatementResponseBody appendChannelStatementLines(String actorAccountId, String sessionId,
        String statementId, AppendChannelStatementLinesRequestBody body) {
        return converter.toResponseBody(appendChannelStatementLinesProcessor.process(
            converter.toAppendRequest(actorAccountId, statementId, body)));
    }

    /** 完成导入。 */
    @Override
    public ChannelStatementCompletionResponseBody completeChannelStatementImport(String actorAccountId,
        String sessionId, String statementId) {
        return converter.toCompletionResponseBody(completeChannelStatementImportProcessor.process(
            new ChannelStatementCommandRequest(Long.valueOf(actorAccountId), Long.valueOf(statementId))));
    }

    /** 作废。 */
    @Override
    public ChannelStatementResponseBody voidChannelStatement(String actorAccountId, String sessionId,
        String statementId) {
        return converter.toResponseBody(voidChannelStatementProcessor.process(
            new ChannelStatementCommandRequest(Long.valueOf(actorAccountId), Long.valueOf(statementId))));
    }

    /** 分页查询账单。 */
    @Override
    public PagingResponseBody<ChannelStatementResponseBody> listChannelStatements(String clearingAccountId,
        String channelBatchRef, String status, String settlementDateFrom, String settlementDateTo, Integer page,
        Integer size) {
        return channelStatementQueryService.listStatements(ReconciliationWire.id(clearingAccountId), channelBatchRef,
            ReconciliationWire.parse(ChannelStatementStatus.class, status), ReconciliationWire.date(settlementDateFrom),
            ReconciliationWire.date(settlementDateTo), PagingRequestBody.of(page, size)).map(converter::toResponseBody);
    }

    /** 账单详情。 */
    @Override
    public ChannelStatementDetailResponseBody getChannelStatement(String statementId) {
        ChannelStatementDetailView view = channelStatementQueryService.getStatement(Long.parseLong(statementId));
        return converter.toDetailResponseBody(view);
    }

    /** 分页查询账单行。 */
    @Override
    public PagingResponseBody<ChannelStatementLineResponseBody> listChannelStatementLines(String statementId,
        String matchStatus, String lineType, Integer page, Integer size) {
        long id = Long.parseLong(statementId);
        String currency = channelStatementQueryService.getStatementHeader(id).getCurrency();
        return channelStatementQueryService.listLines(id, ReconciliationWire.parse(LineMatchStatus.class, matchStatus),
            ReconciliationWire.parse(ChannelLineType.class, lineType), PagingRequestBody.of(page, size))
            .map(line -> converter.toLineResponseBody(line, currency));
    }
}
