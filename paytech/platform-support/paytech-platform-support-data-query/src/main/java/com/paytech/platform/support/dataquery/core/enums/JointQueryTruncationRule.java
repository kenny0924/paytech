package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 服务端单步护栏触发项；页面另行产生层数、总执行数、总时长与手动停止。 */
@Getter
@RequiredArgsConstructor
public enum JointQueryTruncationRule implements CodeEnum {

    /** 某键类型发现的值超过 maxValuesPerKey，只返回前 N 个。 */
    MAX_VALUES_PER_KEY("MAX_VALUES_PER_KEY"),

    /** 本步模板执行数超过 maxExecutionsPerStep，超出的执行被跳过。 */
    STEP_EXECUTION_LIMIT("STEP_EXECUTION_LIMIT"),

    /** 单步预算用尽，未开始的执行被跳过。 */
    STEP_BUDGET("STEP_BUDGET");

    private final String code;
}
