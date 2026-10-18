package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AttemptStatus 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum AttemptStatus implements CodeEnum {
    PROCESSING("PROCESSING"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED"),

    CANCELLED("CANCELLED");

    /** 稳定代码 */
    private final String code;
}