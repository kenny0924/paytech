package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** ActionStatus 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum ActionStatus implements CodeEnum {
    PROCESSING("PROCESSING", false),

    UNKNOWN("UNKNOWN", false),

    SUCCEEDED("SUCCEEDED", true),

    FAILED("FAILED", true);

    /** 稳定代码 */
    final String code;
    final boolean isFinal;
}