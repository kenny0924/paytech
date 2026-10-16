package com.paytech.finance.ext.rest.accounting.converter;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.domain.view.PeriodCloseDetailView;
import com.paytech.finance.accounting.domain.view.PeriodCloseView;
import com.paytech.finance.accounting.service.processor.request.ReviewPeriodCloseRequest;
import com.paytech.finance.accounting.service.processor.request.StartPeriodCloseRequest;
import com.paytech.finance.api.accounting.request.ReviewPeriodCloseRequestBody;
import com.paytech.finance.api.accounting.request.StartPeriodCloseRequestBody;
import com.paytech.finance.api.accounting.response.PeriodCloseDetailResponseBody;
import com.paytech.finance.api.accounting.response.PeriodCloseResponseBody;
import com.paytech.foundation.common.enums.CodeEnum;

/** 期间关账接口的 Wire 模型与内部请求/View 转换；ID 与序号输出为字符串。 */
@Component
public class PeriodCloseConverter {

    /** 转换发起请求。 */
    public StartPeriodCloseRequest toRequest(String periodId, StartPeriodCloseRequestBody body) {
        StartPeriodCloseRequest request = new StartPeriodCloseRequest();
        request.setPeriodId(Long.valueOf(periodId));
        request.setMakerId(Long.valueOf(body.getMakerId()));
        return request;
    }

    /** 转换复核请求。 */
    public ReviewPeriodCloseRequest toRequest(String periodId, String closeNo, ReviewPeriodCloseRequestBody body) {
        ReviewPeriodCloseRequest request = new ReviewPeriodCloseRequest();
        request.setPeriodId(Long.valueOf(periodId));
        request.setCloseNo(Integer.valueOf(closeNo));
        request.setCheckerId(Long.valueOf(body.getCheckerId()));
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
