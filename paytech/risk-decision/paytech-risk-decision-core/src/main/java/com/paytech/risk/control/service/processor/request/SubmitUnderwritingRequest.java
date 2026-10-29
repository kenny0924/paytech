package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交承保草稿的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class SubmitUnderwritingRequest {
    /** 操作人账号 ID，记录为提交人。 */
    private final long actorAccountId;

    /** 草稿版本 ID。 */
    @Positive
    private final long underwritingId;

    /** 期望的并发版本。 */
    @Positive
    private final int expectedVersion;

    /** 提交时冻结的输入快照。 */
    @NotNull
    private final UnderwritingInput input;

    /** 提交的结论字段。 */
    @NotNull
    private final UnderwritingTerms terms;

    /** 提交结论。 */
    @NotNull
    private final UnderwritingOutcome outcome;

    /** 拒绝原因；DECLINE 时必填。 */
    private final String declineReason;
}
