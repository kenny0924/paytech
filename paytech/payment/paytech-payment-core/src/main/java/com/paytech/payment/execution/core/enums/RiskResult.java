package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** RiskResult 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum RiskResult implements CodeEnum {
    ALLOW("ALLOW"),

    REVIEW("REVIEW"),

    DENY("DENY"),

    FAILED("FAILED"),

    TIMED_OUT("TIMED_OUT");

    /** 稳定代码 */
    private final String code;
}