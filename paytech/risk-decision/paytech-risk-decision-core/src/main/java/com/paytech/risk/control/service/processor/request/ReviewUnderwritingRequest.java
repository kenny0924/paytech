package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.core.enums.UnderwritingOutcome;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 复核 HIGH 风险承保的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class ReviewUnderwritingRequest {
    /** 复核人账号 ID，记录为决定人；不能是提交人。 */
    private final long actorAccountId;

    /** 待复核版本 ID。 */
    @Positive
    private final long underwritingId;

    /** 期望的并发版本。 */
    @Positive
    private final int expectedVersion;

    /** 复核结论。 */
    @NotNull
    private final UnderwritingOutcome outcome;

    /** 复核意见；DECLINE 时必填并作为拒绝原因。 */
    private final String comment;
}
