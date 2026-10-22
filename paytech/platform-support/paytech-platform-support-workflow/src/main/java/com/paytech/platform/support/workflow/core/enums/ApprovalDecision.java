package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 审批人的决定。 */
@Getter
@RequiredArgsConstructor
public enum ApprovalDecision implements CodeEnum {

    /** 同意。 */
    APPROVE("APPROVE"),

    /** 退回修改：本节点开新一轮。 */
    RETURN("RETURN"),

    /** 驳回：退回实际走过的上一个任务节点。 */
    REJECT("REJECT");

    private final String code;
}
