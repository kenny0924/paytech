package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AuthenticationRequirement 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum AuthenticationRequirement implements CodeEnum {
    NOT_REQUIRED("NOT_REQUIRED"),

    THREE_DS_REQUIRED("THREE_DS_REQUIRED"),

    CHALLENGE_REQUIRED("CHALLENGE_REQUIRED");

    /** 稳定代码 */
    private final String code;
}