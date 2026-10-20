package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.PeriodCloseApi;
import com.paytech.operations.console.bff.integration.FinancePeriodCloseClient;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.ReviewPeriodCloseRequestBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.StartPeriodReopenRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * ledger 期间关账与重开的 HTTP 转发：每个入口只调用一次 ledger，不重试
 * <li>发起关账、发起重开与审批在 ledger 同步执行，经长耗时读取超时的代理调用；列表、详情与驳回使用普通代理。</li>
 * <li>命令把会话 Account ID 与 Session ID 填入操作人 Header；发起关账无请求体。</li>
 * <li>回执的期间主键（及审批、驳回的流程序号）须与请求一致。</li>
 *
 * @author Claude
 */
@Component
public class FinancePeriodCloseClientImpl implements FinancePeriodCloseClient {
    private final PeriodCloseApi api;
    private final PeriodCloseApi longRunningApi;

    /** 普通与长耗时两个 ledger 代理。 */
    public FinancePeriodCloseClientImpl(PeriodCloseApi api,
        @Qualifier("longRunningPeriodCloseApi") PeriodCloseApi longRunningApi) {
        this.api = api;
        this.longRunningApi = longRunningApi;
    }

    /** 流程分页，核对分页回执与所属期间。 */
    @Override
    public PagingResponseBody<PeriodCloseResponseBody> listPeriodCloses(String accountingPeriodId, int page, int size) {
        var closes = page(api.listPeriodCloses(accountingPeriodId, page, size), page, size,
            PeriodCloseResponseBody.class);
        closes.items().forEach(close -> requireSame(accountingPeriodId, close.getPeriodId()));
        return closes;
    }

    /** 流程详情；期间与序号须与请求一致，三个列表不可缺失。 */
    @Override
    public PeriodCloseDetailResponseBody getPeriodClose(String accountingPeriodId, String closeNo) {
        var detail = convert(api.getPeriodClose(accountingPeriodId, closeNo), PeriodCloseDetailResponseBody.class);
        requireSame(accountingPeriodId, detail.getPeriodId());
        requireSame(closeNo, detail.getCloseNo());
        FinanceContractConverter.require(detail.getSteps() != null && detail.getBooks() != null
            && detail.getApprovals() != null);
        return detail;
    }

    /** 发起关账，无请求体。 */
    @Override
    public PeriodCloseResponseBody startPeriodClose(OperatorContext context, String accountingPeriodId) {
        var started = convert(longRunningApi.startPeriodClose(context.accountId(), context.sessionId(),
            accountingPeriodId).getBody(), PeriodCloseResponseBody.class);
        return started(accountingPeriodId, started);
    }

    /** 发起重开。 */
    @Override
    public PeriodCloseResponseBody startPeriodReopen(OperatorContext context, String accountingPeriodId,
        StartPeriodReopenRequestBody request) {
        var started = write(request, () -> longRunningApi.startPeriodReopen(context.accountId(), context.sessionId(),
            accountingPeriodId, convert(request, com.paytech.ledger.api.request.StartPeriodReopenRequestBody.class))
            .getBody(), PeriodCloseResponseBody.class);
        return started(accountingPeriodId, started);
    }

    /** 批准关账/重开（含损益结转或冲回过账）。 */
    @Override
    public PeriodCloseResponseBody approvePeriodClose(OperatorContext context, String accountingPeriodId,
        String closeNo, ReviewPeriodCloseRequestBody request) {
        var reviewed = write(request, () -> longRunningApi.approvePeriodClose(context.accountId(), context.sessionId(),
            accountingPeriodId, closeNo, convert(request, com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody.class)),
            PeriodCloseResponseBody.class);
        return reviewed(accountingPeriodId, closeNo, reviewed);
    }

    /** 驳回关账/重开。 */
    @Override
    public PeriodCloseResponseBody rejectPeriodClose(OperatorContext context, String accountingPeriodId,
        String closeNo, ReviewPeriodCloseRequestBody request) {
        var reviewed = write(request, () -> api.rejectPeriodClose(context.accountId(), context.sessionId(),
            accountingPeriodId, closeNo, convert(request, com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody.class)),
            PeriodCloseResponseBody.class);
        return reviewed(accountingPeriodId, closeNo, reviewed);
    }

    /** 新流程回执：期间一致且带流程序号。 */
    private static PeriodCloseResponseBody started(String accountingPeriodId, PeriodCloseResponseBody started) {
        requireSame(accountingPeriodId, started.getPeriodId());
        requireIdentity(started.getCloseNo());
        return started;
    }

    /** 复核回执：期间与序号一致。 */
    private static PeriodCloseResponseBody reviewed(String accountingPeriodId, String closeNo,
        PeriodCloseResponseBody reviewed) {
        requireSame(accountingPeriodId, reviewed.getPeriodId());
        requireSame(closeNo, reviewed.getCloseNo());
        return reviewed;
    }
}
