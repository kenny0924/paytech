package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationStatementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.AppendChannelStatementLinesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementCompletionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementLineResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.PlatformTxnResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.StartChannelStatementImportRequestBody;
import com.paytech.reconciliation.api.statement.ChannelStatementApi;
import com.paytech.reconciliation.api.statement.PlatformTxnApi;

import lombok.AllArgsConstructor;

/**
 * reconciliation 渠道账单与平台流水的 HTTP 转发：每个入口只调用一次 reconciliation，不重试
 * <li>账单行列表的元素须属于请求账单；详情、追加、完成与作废回执的账单主键须与请求一致。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconciliationStatementClientImpl implements ReconciliationStatementClient {
    private final ChannelStatementApi statementApi;
    private final PlatformTxnApi platformTxnApi;

    /** 账单分页，核对分页回执。 */
    @Override
    public PagingResponseBody<ChannelStatementResponseBody> listChannelStatements(String clearingAccountId,
        String channelBatchRef, String status, String settlementDateFrom, String settlementDateTo, int page, int size) {
        return page(statementApi.listChannelStatements(clearingAccountId, channelBatchRef, status, settlementDateFrom,
            settlementDateTo, page, size), page, size, ChannelStatementResponseBody.class);
    }

    /** 账单详情；账单头不可缺失且主键一致。 */
    @Override
    public ChannelStatementDetailResponseBody getChannelStatement(String statementId) {
        var detail = convert(statementApi.getChannelStatement(statementId), ChannelStatementDetailResponseBody.class);
        require(detail.getStatement() != null);
        requireSame(statementId, detail.getStatement().getStatementId());
        return detail;
    }

    /** 账单行分页；元素须属于请求账单。 */
    @Override
    public PagingResponseBody<ChannelStatementLineResponseBody> listChannelStatementLines(String statementId,
        String matchStatus, String lineType, int page, int size) {
        var lines = page(statementApi.listChannelStatementLines(statementId, matchStatus, lineType, page, size), page,
            size, ChannelStatementLineResponseBody.class);
        lines.items().forEach(line -> requireSame(statementId, line.getStatementId()));
        return lines;
    }

    /** 开始导入；回执带新主键。 */
    @Override
    public ChannelStatementResponseBody startChannelStatementImport(OperatorContext context,
        StartChannelStatementImportRequestBody request) {
        var started = write(request, () -> statementApi.startChannelStatementImport(context.accountId(),
            context.sessionId(), convert(request,
                com.paytech.reconciliation.api.statement.request.StartChannelStatementImportRequestBody.class)).getBody(),
            ChannelStatementResponseBody.class);
        requireIdentity(started.getStatementId());
        return started;
    }

    /** 追加一段行；回执主键一致。 */
    @Override
    public ChannelStatementResponseBody appendChannelStatementLines(OperatorContext context, String statementId,
        AppendChannelStatementLinesRequestBody request) {
        var statement = write(request, () -> statementApi.appendChannelStatementLines(context.accountId(),
            context.sessionId(), statementId, convert(request,
                com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody.class)),
            ChannelStatementResponseBody.class);
        requireSame(statementId, statement.getStatementId());
        return statement;
    }

    /** 完成导入；回执主键一致，拒绝原因集合不可缺失。 */
    @Override
    public ChannelStatementCompletionResponseBody completeChannelStatementImport(OperatorContext context,
        String statementId) {
        var completion = convert(statementApi.completeChannelStatementImport(context.accountId(), context.sessionId(),
            statementId), ChannelStatementCompletionResponseBody.class);
        requireSame(statementId, completion.getStatementId());
        require(completion.getRejections() != null);
        return completion;
    }

    /** 作废；回执主键一致。 */
    @Override
    public ChannelStatementResponseBody voidChannelStatement(OperatorContext context, String statementId) {
        var statement = convert(statementApi.voidChannelStatement(context.accountId(), context.sessionId(), statementId),
            ChannelStatementResponseBody.class);
        requireSame(statementId, statement.getStatementId());
        return statement;
    }

    /** 平台流水分页，核对分页回执。 */
    @Override
    public PagingResponseBody<PlatformTxnResponseBody> listPlatformTxns(String clearingAccountId, String statementId,
        String status,
        String direction, String externalTransactionId, String businessDateFrom, String businessDateTo, int page,
        int size) {
        return page(platformTxnApi.listPlatformTxns(clearingAccountId, statementId, status, direction, externalTransactionId,
            businessDateFrom, businessDateTo, page, size), page, size, PlatformTxnResponseBody.class);
    }
}
