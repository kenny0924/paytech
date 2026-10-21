package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationExceptionClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.OpenReconExceptionRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconResolutionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReviewReconResolutionRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.SubmitReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.ReconExceptionApi;
import com.paytech.reconciliation.api.exception.ReconResolutionApi;
import com.paytech.reconciliation.api.summary.ReconSummaryApi;

import lombok.AllArgsConstructor;

/**
 * reconciliation 对账异常、处置与在途汇总的 HTTP 转发：每个入口只调用一次 reconciliation，不重试
 * <li>详情的异常主键、提交回执的异常主键与复核回执的处置主键须与请求一致；详情的处置与可选项集合不可缺失。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconciliationExceptionClientImpl implements ReconciliationExceptionClient {
    private final ReconSummaryApi summaryApi;
    private final ReconExceptionApi exceptionApi;
    private final ReconResolutionApi resolutionApi;

    /** 异常分页，核对分页回执。 */
    @Override
    public PagingResponseBody<ReconExceptionResponseBody> listReconExceptions(String status, String exceptionType,
        String reconLayer, String clearingAccountId, String cashAccountId, String statementId, int page, int size) {
        return page(exceptionApi.listReconExceptions(status, exceptionType, reconLayer, clearingAccountId,
            cashAccountId, statementId, page, size), page, size, ReconExceptionResponseBody.class);
    }

    /** 异常详情；主键一致，来源摘要、可选项与处置集合不可缺失。 */
    @Override
    public ReconExceptionDetailResponseBody getReconException(String exceptionId) {
        var detail = convert(exceptionApi.getReconException(exceptionId), ReconExceptionDetailResponseBody.class);
        require(detail.getException() != null && detail.getSource() != null && detail.getResolutions() != null
            && detail.getAvailableResolutionTypes() != null && detail.getAvailableClassificationCodes() != null);
        requireSame(exceptionId, detail.getException().getExceptionId());
        return detail;
    }

    /** 人工开立；回执带新主键且来源一致。 */
    @Override
    public ReconExceptionResponseBody openReconException(OperatorContext context, OpenReconExceptionRequestBody request) {
        var opened = write(request, () -> exceptionApi.openReconException(context.accountId(), context.sessionId(),
            convert(request, com.paytech.reconciliation.api.exception.request.OpenReconExceptionRequestBody.class))
            .getBody(), ReconExceptionResponseBody.class);
        requireIdentity(opened.getExceptionId());
        requireSame(request.getSourceId(), opened.getSourceId());
        return opened;
    }

    /** 提交处置；回执带新主键且异常一致。 */
    @Override
    public ReconResolutionResponseBody submitReconResolution(OperatorContext context, String exceptionId,
        SubmitReconResolutionRequestBody request) {
        var submitted = write(request, () -> exceptionApi.submitReconResolution(context.accountId(),
            context.sessionId(), exceptionId, convert(request,
                com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody.class)).getBody(),
            ReconResolutionResponseBody.class);
        requireIdentity(submitted.getResolutionId());
        requireSame(exceptionId, submitted.getExceptionId());
        return submitted;
    }

    /** 复核处置；回执处置主键一致。 */
    @Override
    public ReconResolutionResponseBody reviewReconResolution(OperatorContext context, String resolutionId,
        ReviewReconResolutionRequestBody request) {
        var reviewed = write(request, () -> resolutionApi.reviewReconResolution(context.accountId(),
            context.sessionId(), resolutionId, convert(request,
                com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody.class)),
            ReconResolutionResponseBody.class);
        requireSame(resolutionId, reviewed.getResolutionId());
        return reviewed;
    }

    /** 在途汇总；币种一致，三块不可缺失。 */
    @Override
    public ReconSummaryResponseBody getReconSummary(String entityNo, String currency) {
        var summary = convert(summaryApi.getReconSummary(entityNo, currency), ReconSummaryResponseBody.class);
        requireSame(currency, summary.getCurrency());
        require(summary.getInTransitStatements() != null && summary.getOpenExceptionsByAge() != null
            && summary.getUnmatchedBankLines() != null);
        return summary;
    }
}
