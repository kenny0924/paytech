package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 多审批人的放行方式。 */
@Getter
@RequiredArgsConstructor
public enum ApprovalMode implements CodeEnum {

    /** 任一人同意即放行。 */
    ANY("ANY"),

    /** 全部审批人同意才放行。 */
    ALL("ALL");

    private final String code;
}
