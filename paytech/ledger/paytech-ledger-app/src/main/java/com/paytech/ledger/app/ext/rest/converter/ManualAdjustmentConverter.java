package com.paytech.ledger.app.ext.rest.converter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody;
import com.paytech.ledger.api.request.SubmitManualAdjustmentRequestBody;
import com.paytech.ledger.api.response.ManualAdjustmentDetailResponseBody;
import com.paytech.ledger.api.response.ReviewManualAdjustmentResponseBody;
import com.paytech.ledger.api.response.SubmitManualAdjustmentResponseBody;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.domain.view.ManualAdjustmentDetailView;
import com.paytech.ledger.service.processor.request.ReviewManualAdjustmentRequest;
import com.paytech.ledger.service.processor.request.SubmitManualAdjustmentRequest;
import com.paytech.ledger.service.processor.response.ReviewManualAdjustmentResponse;
import com.paytech.ledger.service.processor.response.SubmitManualAdjustmentResponse;

/** 人工调整接口的 Wire 模型与内部请求/结果转换；金额字符串与 BigDecimal 精确互转。 */
@Component
public class ManualAdjustmentConverter {

    /** 转换提交请求。 */
    public SubmitManualAdjustmentRequest toRequest(SubmitManualAdjustmentRequestBody body) {
        SubmitManualAdjustmentRequest request = new SubmitManualAdjustmentRequest();
        request.setLedgerBookId(Long.valueOf(body.getLedgerBookId()));
        request.setAccountingPeriodId(Long.valueOf(body.getAccountingPeriodId()));
        request.setCurrency(body.getCurrency());
        request.setReasonCode(body.getReasonCode());
        request.setFileId(body.getFileId() == null ? null : Long.valueOf(body.getFileId()));
        request.setFileContentHash(body.getFileContentHash());
        request.setMakerId(Long.valueOf(body.getMakerId()));
        request.setLines(body.getLines().stream().map(this::toLine).toList());
        return request;
    }

    /** 转换一条明细。 */
    SubmitManualAdjustmentRequest.Line toLine(SubmitManualAdjustmentRequestBody.Line body) {
        SubmitManualAdjustmentRequest.Line line = new SubmitManualAdjustmentRequest.Line();
        line.setAccountId(Long.valueOf(body.getAccountId()));
        line.setEntrySide(CodeEnumConverter.parse(EntrySide.class, body.getEntrySide()));
        line.setAmount(new BigDecimal(body.getAmount()));
        line.setLineDescription(body.getLineDescription());
        return line;
    }

    /** 转换复核请求。 */
    public ReviewManualAdjustmentRequest toRequest(String manualAdjustmentId, ReviewManualAdjustmentRequestBody body) {
        ReviewManualAdjustmentRequest request = new ReviewManualAdjustmentRequest();
        request.setManualAdjustmentId(Long.valueOf(manualAdjustmentId));
        request.setCheckerId(Long.valueOf(body.getCheckerId()));
        request.setApprovedContentHash(body.getApprovedContentHash());
        return request;
    }

    /** 转换提交结果。 */
    public SubmitManualAdjustmentResponseBody toResponseBody(SubmitManualAdjustmentResponse response) {
        SubmitManualAdjustmentResponseBody body = new SubmitManualAdjustmentResponseBody();
        body.setManualAdjustmentId(response.getManualAdjustmentId().toString());
        body.setAdjustmentNo(response.getAdjustmentNo());
        body.setContentHash(response.getContentHash());
        body.setStatus(response.getStatus().getCode());
        return body;
    }

    /** 转换复核结果；驳回时分录字段为空。 */
    public ReviewManualAdjustmentResponseBody toResponseBody(ReviewManualAdjustmentResponse response) {
        ReviewManualAdjustmentResponseBody body = new ReviewManualAdjustmentResponseBody();
        body.setManualAdjustmentId(response.getManualAdjustmentId().toString());
        body.setStatus(response.getStatus().getCode());
        body.setJournalEntryId(text(response.getJournalEntryId()));
        body.setJournalSequence(text(response.getJournalSequence()));
        body.setPostingDate(response.getPostingDate());
        return body;
    }

    /** 转换调整详情。 */
    public ManualAdjustmentDetailResponseBody toResponseBody(ManualAdjustmentDetailView view) {
        ManualAdjustmentDetailResponseBody body = new ManualAdjustmentDetailResponseBody();
        body.setManualAdjustmentId(view.getManualAdjustmentId().toString());
        body.setAdjustmentNo(view.getAdjustmentNo());
        body.setLedgerBookId(view.getLedgerBookId().toString());
        body.setAccountingPeriodId(view.getAccountingPeriodId().toString());
        body.setCurrency(view.getCurrency());
        body.setReasonCode(view.getReasonCode());
        body.setFileId(text(view.getFileId()));
        body.setFileContentHash(view.getFileContentHash());
        body.setTotalDebit(view.getTotalDebit().toPlainString());
        body.setTotalCredit(view.getTotalCredit().toPlainString());
        body.setContentHash(view.getContentHash());
        body.setStatus(view.getStatus().getCode());
        body.setMakerId(view.getMakerId().toString());
        body.setSubmittedTime(view.getSubmittedTime());
        body.setLines(view.getLines().stream().map(this::toLineBody).toList());
        body.setApprovals(view.getApprovals().stream().map(this::toApprovalBody).toList());
        body.setJournalEntryId(text(view.getJournalEntryId()));
        body.setJournalSequence(text(view.getJournalSequence()));
        body.setPostingDate(view.getPostingDate());
        return body;
    }

    /** 转换详情明细。 */
    ManualAdjustmentDetailResponseBody.Line toLineBody(ManualAdjustmentDetailView.Line line) {
        ManualAdjustmentDetailResponseBody.Line body = new ManualAdjustmentDetailResponseBody.Line();
        body.setLineNo(line.getLineNo());
        body.setAccountId(line.getAccountId().toString());
        body.setEntrySide(line.getEntrySide().getCode());
        body.setAmount(line.getAmount().toPlainString());
        body.setLineDescription(line.getLineDescription());
        return body;
    }

    /** 转换审批记录。 */
    ManualAdjustmentDetailResponseBody.Approval toApprovalBody(ManualAdjustmentDetailView.Approval approval) {
        ManualAdjustmentDetailResponseBody.Approval body = new ManualAdjustmentDetailResponseBody.Approval();
        body.setApprovalRole(approval.getApprovalRole().getCode());
        body.setActorId(approval.getActorId().toString());
        body.setDecision(approval.getDecision().getCode());
        body.setApprovedContentHash(approval.getApprovedContentHash());
        body.setDecisionTime(approval.getDecisionTime());
        return body;
    }

    /** 可空 Long 转十进制字符串。 */
    String text(Long value) {
        return value == null ? null : value.toString();
    }
}
