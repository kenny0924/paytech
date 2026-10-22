package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 驳回在当前位置的处理方式。 */
@Getter
@RequiredArgsConstructor
public enum RejectionKind implements CodeEnum {

    /** 退回实际走过的上一个 TASK 节点。 */
    PREVIOUS_TASK("PREVIOUS_TASK"),

    /** 汇合后的第一个节点：须指定退回哪一条分支的最后一个 TASK 节点。 */
    CHOOSE_BRANCH("CHOOSE_BRANCH"),

    /** 流程第一个任务节点，不能驳回。 */
    FIRST_TASK("FIRST_TASK"),

    /** 并行分支的第一个节点，不能驳回。 */
    BRANCH_FIRST_NODE("BRANCH_FIRST_NODE");

    private final String code;
}
