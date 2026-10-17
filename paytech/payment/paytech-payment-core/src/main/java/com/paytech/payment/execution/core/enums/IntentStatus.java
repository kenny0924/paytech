package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** IntentStatus 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum IntentStatus implements CodeEnum {
    OPEN("OPEN"),

    SUCCEEDED("SUCCEEDED"),

    CANCELLED("CANCELLED"),

    EXPIRED("EXPIRED");

    /** 稳定代码 */
    private final String code;
}
