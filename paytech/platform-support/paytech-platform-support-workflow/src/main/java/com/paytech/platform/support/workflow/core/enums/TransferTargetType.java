package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 转交对象类型，与 t_workflow_transfer.target_type 一致。 */
@Getter
@RequiredArgsConstructor
public enum TransferTargetType implements CodeEnum {

    /** 动作运行。 */
    ACTION_RUN("ACTION_RUN"),

    /** 审批行。 */
    APPROVAL("APPROVAL");

    private final String code;
}
