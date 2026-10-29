package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** DATA_QUERY 动作最近一次检查的结果（t_workflow_action_run.check_result）。 */
@Getter
@RequiredArgsConstructor
public enum DataQueryCheckResult implements CodeEnum {

    /** 查询返回 satisfied 为真，动作随之完成。 */
    SATISFIED("SATISFIED"),

    /** 查询成功执行但未满足，动作保持 PENDING。 */
    UNSATISFIED("UNSATISFIED"),

    /** 查询失败、超时或返回形状不符，动作保持 PENDING。 */
    FAILED("FAILED");

    private final String code;
}
