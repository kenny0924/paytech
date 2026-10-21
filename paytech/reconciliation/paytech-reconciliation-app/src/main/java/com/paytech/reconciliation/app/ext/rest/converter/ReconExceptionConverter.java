package com.paytech.reconciliation.app.ext.rest.converter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.paytech.foundation.money.Money;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.RematchTargetType;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.exception.request.OpenReconExceptionRequestBody;
import com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.response.ReconExceptionDetailResponseBody;
import com.paytech.reconciliation.api.exception.response.ReconExceptionResponseBody;
import com.paytech.reconciliation.api.exception.response.ReconResolutionResponseBody;
import com.paytech.reconciliation.api.summary.response.ReconSummaryResponseBody;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domain.view.ReconExceptionDetailView;
import com.paytech.reconciliation.domain.view.ReconSummaryView;
import com.paytech.reconciliation.service.processor.request.OpenReconExceptionRequest;
import com.paytech.reconciliation.service.processor.request.ReviewReconResolutionRequest;
import com.paytech.reconciliation.service.processor.request.SubmitReconResolutionRequest;
import lombok.AllArgsConstructor;

/** 对账异常、处置与在途汇总的 HTTP 协议转换；账龄按当前 UTC 时间计算整天数。 */
@Component
@AllArgsConstructor
public class ReconExceptionConverter {
    /** 汇总账龄段，固定顺序。 */
    static final List<String> AGE_BUCKETS = List.of("D0_3", "D4_7", "D8_30", "D31_PLUS");

    private final Clock clock;

    /** 人工开异常命令。 */
    public OpenReconExceptionRequest toOpenRequest(String actorAccountId, OpenReconExceptionRequestBody body) {
        return new OpenReconExceptionRequest(Long.valueOf(actorAccountId),
            ReconciliationWire.parse(ReconExceptionSourceType.class, body.getSourceType()), Long.valueOf(body.getSourceId()));
    }

    /** 提交处置命令。 */
    public SubmitReconResolutionRequest toSubmitRequest(String actorAccountId, String exceptionId,
        SubmitReconResolutionRequestBody body) {
        return SubmitReconResolutionRequest.builder().actorId(Long.valueOf(actorAccountId))
            .exceptionId(Long.valueOf(exceptionId))
            .resolutionType(ReconciliationWire.parse(ResolutionType.class, body.getResolutionType()))
            .classificationCode(ReconciliationWire.parse(ReconClassification.class, body.getClassificationCode()))
            .amount(ReconciliationWire.decimal(body.getAmount()))
            .rematchTargetType(ReconciliationWire.parse(RematchTargetType.class, body.getRematchTargetType()))
            .rematchTargetId(ReconciliationWire.id(body.getRematchTargetId()))
            .rematchAmount(ReconciliationWire.decimal(body.getRematchAmount())).note(body.getNote()).build();
    }

    /** 复核处置命令。 */
    public ReviewReconResolutionRequest toReviewRequest(String actorAccountId, String resolutionId,
        ReviewReconResolutionRequestBody body) {
        return new ReviewReconResolutionRequest(Long.valueOf(actorAccountId), Long.valueOf(resolutionId),
            ReconciliationWire.parse(CheckerDecision.class, body.getDecision()), body.getApprovedContentHash());
    }

    /** 异常响应（含账龄）。 */
    public ReconExceptionResponseBody toExceptionBody(ReconException exception) {
        String currency = exception.getCurrency();
        ReconExceptionResponseBody body = new ReconExceptionResponseBody();
        body.setExceptionId(exception.getId().toString());
        body.setExceptionType(exception.getExceptionType().getCode());
        body.setReconLayer(exception.getReconLayer().getCode());
        body.setBlocking(exception.getBlocking());
        body.setSourceType(exception.getSourceType().getCode());
        body.setSourceId(exception.getSourceId().toString());
        body.setStatementId(ReconciliationWire.text(exception.getStatementId()));
        body.setClearingAccountId(ReconciliationWire.text(exception.getClearingAccountId()));
        body.setCashAccountId(ReconciliationWire.text(exception.getCashAccountId()));
        body.setEntityType(exception.getEntityType().getCode());
        body.setEntityId(exception.getEntityId());
        body.setCurrency(currency);
        body.setDifferenceAmount(ReconciliationWire.amount(currency, exception.getDifferenceAmount()));
        body.setDetail(exception.getDetail());
        body.setStatus(exception.getStatus().getCode());
        body.setOpenedBy(ReconciliationWire.text(exception.getOpenedBy()));
        body.setOpenedTime(exception.getOpenedTime());
        Instant end = exception.getClosedTime() != null ? exception.getClosedTime() : clock.instant();
        body.setAgeDays((int)Duration.between(exception.getOpenedTime(), end).toDays());
        body.setClosedTime(exception.getClosedTime());
        body.setVersion(exception.getVersion().toString());
        return body;
    }

    /** 处置响应。 */
    public ReconResolutionResponseBody toResolutionBody(ReconResolution resolution, String currency) {
        ReconResolutionResponseBody body = new ReconResolutionResponseBody();
        body.setResolutionId(resolution.getId().toString());
        body.setExceptionId(resolution.getExceptionId().toString());
        body.setResolutionNo(resolution.getResolutionNo());
        body.setResolutionType(resolution.getResolutionType().getCode());
        body.setClassificationCode(ReconciliationWire.code(resolution.getClassificationCode()));
        body.setAmount(ReconciliationWire.amount(currency, resolution.getAmount()));
        body.setRematchTargetType(ReconciliationWire.code(resolution.getRematchTargetType()));
        body.setRematchTargetId(ReconciliationWire.text(resolution.getRematchTargetId()));
        body.setRematchAmount(ReconciliationWire.amount(currency, resolution.getRematchAmount()));
        body.setNote(resolution.getNote());
        body.setContentHash(resolution.getContentHash());
        body.setMakerId(resolution.getMakerId().toString());
        body.setCheckerId(ReconciliationWire.text(resolution.getCheckerId()));
        body.setCheckerDecision(ReconciliationWire.code(resolution.getCheckerDecision()));
        body.setDecisionTime(resolution.getDecisionTime());
        body.setStatus(resolution.getStatus().getCode());
        body.setCreateTime(resolution.getCreateTime());
        return body;
    }

    /** 异常详情响应。 */
    public ReconExceptionDetailResponseBody toDetailBody(ReconExceptionDetailView view) {
        ReconException exception = view.getException();
        String currency = exception.getCurrency();
        ReconExceptionDetailView.SourceSummary summary = view.getSource();
        ReconExceptionDetailResponseBody.SourceSummary source = new ReconExceptionDetailResponseBody.SourceSummary();
        source.setSourceType(exception.getSourceType().getCode());
        source.setSourceId(exception.getSourceId().toString());
        source.setReference(summary.getReference());
        source.setDirection(summary.getDirection());
        source.setAmount(ReconciliationWire.amount(currency, summary.getAmount()));
        source.setStatus(summary.getStatus());
        source.setBusinessDate(summary.getBusinessDate());
        ReconExceptionDetailResponseBody body = new ReconExceptionDetailResponseBody();
        body.setException(toExceptionBody(exception));
        body.setSource(source);
        body.setAvailableResolutionTypes(view.getAvailableResolutionTypes().stream().map(ResolutionType::getCode).toList());
        body.setAvailableClassificationCodes(view.getAvailableClassificationCodes().stream()
            .map(ReconClassification::getCode).toList());
        body.setResolutions(view.getResolutions().stream().map(resolution -> toResolutionBody(resolution, currency))
            .toList());
        return body;
    }

    /** 在途汇总响应：缺少的状态 / 账龄段 / 方向补 0。 */
    public ReconSummaryResponseBody toSummaryBody(String entityNo, String currency, ReconSummaryView view) {
        ReconSummaryResponseBody body = new ReconSummaryResponseBody();
        body.setEntityNo(entityNo);
        body.setCurrency(currency);
        body.setInTransitStatements(inTransit(currency, view.getStatementTotals()));
        Map<String, ReconSummaryView.ExceptionAgeTotal> buckets = view.getExceptionAgeTotals().stream()
            .collect(Collectors.toMap(ReconSummaryView.ExceptionAgeTotal::getBucket, Function.identity()));
        body.setOpenExceptionsByAge(AGE_BUCKETS.stream().map(bucket -> {
            ReconSummaryResponseBody.ExceptionAgeBucket item = new ReconSummaryResponseBody.ExceptionAgeBucket();
            ReconSummaryView.ExceptionAgeTotal total = buckets.get(bucket);
            item.setBucket(bucket);
            item.setCount(total == null ? 0 : total.getExceptionCount());
            item.setBlockingCount(total == null ? 0 : total.getBlockingCount());
            return item;
        }).toList());
        body.setUnmatchedBankLines(unmatched(currency, view.getUnmatchedLineTotals()));
        return body;
    }

    /** 在途账单块。 */
    static ReconSummaryResponseBody.InTransitStatements inTransit(String currency,
        List<ReconSummaryView.StatementTotal> totals) {
        ReconSummaryResponseBody.InTransitStatements block = new ReconSummaryResponseBody.InTransitStatements();
        block.setMatchingCount(0);
        block.setMatchingNetAmount(zero(currency));
        block.setReconciledCount(0);
        block.setReconciledUnconfirmedAmount(zero(currency));
        for (ReconSummaryView.StatementTotal total : totals) {
            if (ChannelStatementStatus.MATCHING.getCode().equals(total.getStatus())) {
                block.setMatchingCount(total.getStatementCount());
                block.setMatchingNetAmount(ReconciliationWire.amount(currency, total.getSignedNetAmount()));
            } else {
                block.setReconciledCount(total.getStatementCount());
                block.setReconciledUnconfirmedAmount(ReconciliationWire.amount(currency,
                    total.getSignedNetAmount().subtract(total.getCashConfirmedAmount())));
            }
        }
        return block;
    }

    /** 未匹配银行流水块。 */
    static ReconSummaryResponseBody.UnmatchedBankLines unmatched(String currency,
        List<ReconSummaryView.UnmatchedLineTotal> totals) {
        ReconSummaryResponseBody.UnmatchedBankLines block = new ReconSummaryResponseBody.UnmatchedBankLines();
        block.setCount(0);
        block.setCreditAmount(zero(currency));
        block.setDebitAmount(zero(currency));
        for (ReconSummaryView.UnmatchedLineTotal total : totals) {
            block.setCount(block.getCount() + total.getLineCount());
            if (SettlementDirection.CREDIT.getCode().equals(total.getDirection())) {
                block.setCreditAmount(ReconciliationWire.amount(currency, total.getUnmatchedAmount()));
            } else {
                block.setDebitAmount(ReconciliationWire.amount(currency, total.getUnmatchedAmount()));
            }
        }
        return block;
    }

    /** 按币种精度的 0。 */
    static String zero(String currency) {
        return Money.of(currency, BigDecimal.ZERO).toAmountString();
    }
}
