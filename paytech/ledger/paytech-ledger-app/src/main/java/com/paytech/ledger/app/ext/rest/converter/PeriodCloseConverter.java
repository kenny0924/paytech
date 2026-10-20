package com.paytech.ledger.app.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody;
import com.paytech.ledger.api.request.StartPeriodReopenRequestBody;
import com.paytech.ledger.api.response.PeriodCloseDetailResponseBody;
import com.paytech.ledger.api.response.PeriodCloseResponseBody;
import com.paytech.ledger.domain.view.PeriodCloseDetailView;
import com.paytech.ledger.domain.view.PeriodCloseView;
import com.paytech.ledger.service.processor.request.ReviewPeriodCloseRequest;
import com.paytech.ledger.service.processor.request.StartPeriodCloseRequest;
import com.paytech.ledger.service.processor.request.StartPeriodReopenRequest;

/** 期间关账与重开接口的 Wire 模型与内部请求/View 转换；ID 与序号输出为字符串，编制人 / 复核人取自操作人 Header（已由接口校验为正十进制）。 */
@Component
public class PeriodCloseConverter {

    /** 转换发起请求，编制人取自操作人 Header。 */
    public StartPeriodCloseRequest toStartRequest(String periodId, String operatorId) {
        StartPeriodCloseRequest request = new StartPeriodCloseRequest();
        request.setPeriodId(Long.valueOf(periodId));
        request.setMakerId(Long.valueOf(operatorId));
        return request;
    }

    /** 转换发起重开请求，编制人取自操作人 Header；原因代码合法性由 Processor 校验。 */
    public StartPeriodReopenRequest toRequest(String periodId, StartPeriodReopenRequestBody body, String operatorId) {
        StartPeriodReopenRequest request = new StartPeriodReopenRequest();
        request.setPeriodId(Long.valueOf(periodId));
        request.setMakerId(Long.valueOf(operatorId));
        request.setReasonCode(body.getReasonCode());
        request.setRequestRef(body.getRequestRef());
        return request;
    }

    /** 转换复核请求，复核人取自操作人 Header。 */
    public ReviewPeriodCloseRequest toRequest(String periodId, String closeNo, ReviewPeriodCloseRequestBody body,
        String operatorId) {
        ReviewPeriodCloseRequest request = new ReviewPeriodCloseRequest();
        request.setPeriodId(Long.valueOf(periodId));
        request.setCloseNo(Integer.valueOf(closeNo));
        request.setCheckerId(Long.valueOf(operatorId));
        request.setApprovedCloseHash(body.getApprovedCloseHash());
        return request;
    }

    /** 转换流程摘要。 */
    public PeriodCloseResponseBody toResponseBody(PeriodCloseView view) {
        PeriodCloseResponseBody body = new PeriodCloseResponseBody();
        body.setPeriodCloseId(view.getPeriodCloseId().toString());
        body.setPeriodId(view.getPeriodId().toString());
        body.setCloseNo(view.getCloseNo().toString());
        body.setCloseType(view.getCloseType().getCode());
        body.setStatus(view.getStatus().getCode());
        body.setMakerId(view.getMakerId().toString());
        body.setCheckerId(text(view.getCheckerId()));
        body.setCloseHash(view.getCloseHash());
        body.setFailedStepType(CodeEnum.defaultCode(view.getFailedStepType()));
        body.setReopensCloseNo(view.getReopensCloseNo() == null ? null : view.getReopensCloseNo().toString());
        body.setReopenReasonCode(view.getReopenReasonCode());
        body.setReopenRequestRef(view.getReopenRequestRef());
        body.setCreateTime(view.getCreateTime());
        return body;
    }

    /** 转换流程详情。 */
    public PeriodCloseDetailResponseBody toResponseBody(PeriodCloseDetailView view) {
        PeriodCloseResponseBody summary = toResponseBody(view.getSummary());
        PeriodCloseDetailResponseBody body = new PeriodCloseDetailResponseBody();
        body.setPeriodCloseId(summary.getPeriodCloseId());
        body.setPeriodId(summary.getPeriodId());
        body.setCloseNo(summary.getCloseNo());
        body.setCloseType(summary.getCloseType());
        body.setStatus(summary.getStatus());
        body.setMakerId(summary.getMakerId());
        body.setCheckerId(summary.getCheckerId());
        body.setCloseHash(summary.getCloseHash());
        body.setFailedStepType(summary.getFailedStepType());
        body.setReopensCloseNo(summary.getReopensCloseNo());
        body.setReopenReasonCode(summary.getReopenReasonCode());
        body.setReopenRequestRef(summary.getReopenRequestRef());
        body.setCreateTime(summary.getCreateTime());
        body.setSteps(view.getSteps().stream().map(this::toStepBody).toList());
        body.setBooks(view.getBooks().stream().map(this::toBookBody).toList());
        body.setApprovals(view.getApprovals().stream().map(this::toApprovalBody).toList());
        return body;
    }

    /** 转换步骤。 */
    PeriodCloseDetailResponseBody.Step toStepBody(PeriodCloseDetailView.Step step) {
        PeriodCloseDetailResponseBody.Step body = new PeriodCloseDetailResponseBody.Step();
        body.setStepNo(step.getStepNo());
        body.setStepType(step.getStepType().getCode());
        body.setStatus(step.getStatus().getCode());
        body.setResultHash(step.getResultHash());
        body.setCompletedTime(step.getCompletedTime());
        return body;
    }

    /** 转换账簿证据。 */
    PeriodCloseDetailResponseBody.Book toBookBody(PeriodCloseDetailView.Book book) {
        PeriodCloseDetailResponseBody.Book body = new PeriodCloseDetailResponseBody.Book();
        body.setLedgerBookId(book.getLedgerBookId().toString());
        body.setAsOfSequence(book.getAsOfSequence().toString());
        body.setFinalTrialRunId(text(book.getFinalTrialRunId()));
        body.setPostingInstructionId(text(book.getPostingInstructionId()));
        body.setStatus(book.getStatus().getCode());
        return body;
    }

    /** 转换审批记录。 */
    PeriodCloseDetailResponseBody.Approval toApprovalBody(PeriodCloseDetailView.Approval approval) {
        PeriodCloseDetailResponseBody.Approval body = new PeriodCloseDetailResponseBody.Approval();
        body.setApprovalRole(approval.getApprovalRole().getCode());
        body.setActorId(approval.getActorId().toString());
        body.setDecision(approval.getDecision().getCode());
        body.setApprovedCloseHash(approval.getApprovedCloseHash());
        body.setDecisionTime(approval.getDecisionTime());
        return body;
    }

    /** 可空 Long 转十进制字符串。 */
    String text(Long value) {
        return value == null ? null : value.toString();
    }
}
