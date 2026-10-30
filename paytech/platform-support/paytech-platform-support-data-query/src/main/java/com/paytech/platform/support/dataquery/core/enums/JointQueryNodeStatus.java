package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 联合查询一次模板执行（节点）的结果；单个节点失败或跳过不影响同一步的其他节点。 */
@Getter
@RequiredArgsConstructor
public enum JointQueryNodeStatus implements CodeEnum {

    /** 执行成功。 */
    SUCCEEDED("SUCCEEDED"),

    /** 执行失败：超时、数据源不可用、只读校验拒绝、参数错误等。 */
    FAILED("FAILED"),

    /** 未执行：单步预算或单步执行数用尽、执行器繁忙。 */
    SKIPPED("SKIPPED");

    private final String code;
}
