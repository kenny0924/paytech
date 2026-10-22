package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 审批行状态，与 t_workflow_approval.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum ApprovalStatus implements CodeEnum {

    /** 待决定。 */
    PENDING("PENDING"),

    /** 同意。 */
    APPROVED("APPROVED"),

    /** 退回修改。 */
    RETURNED("RETURNED"),

    /** 驳回。 */
    REJECTED("REJECTED"),

    /** 因他人决定或实例终止撤回。 */
    WITHDRAWN("WITHDRAWN");

    private final String code;
}
