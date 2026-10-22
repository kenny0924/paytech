package com.paytech.platform.support.workflow.service.processor.request;

import com.paytech.platform.support.workflow.core.enums.ApprovalDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审批决定的内部请求。 */
@Getter
@AllArgsConstructor
public class DecideApprovalRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 审批 ID。 */
    @NotNull
    private final Long approvalId;

    /** 决定。 */
    @NotNull
    private final ApprovalDecision decision;

    /** 意见；可空。 */
    private final String comment;

    /** 驳回目标；可空。 */
    private final String rejectTargetNodeKey;
}
