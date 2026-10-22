package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 动作运行状态，与 t_workflow_action_run.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum ActionRunStatus implements CodeEnum {

    /** 未提交。 */
    PENDING("PENDING"),

    /** 最后一次验证通过，不再接受提交。 */
    VERIFIED("VERIFIED"),

    /** 最后一次验证未通过，可重新提交。 */
    UNVERIFIED("UNVERIFIED"),

    /** 节点结束或实例终止时未完成。 */
    CANCELLED("CANCELLED");

    private final String code;
}
