package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** InteractionType 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum InteractionType implements CodeEnum {
    REDIRECT("REDIRECT"),

    THREE_DS_CHALLENGE("THREE_DS_CHALLENGE"),

    SDK("SDK"),

    APP_SWITCH("APP_SWITCH"),

    QR_CODE("QR_CODE");

    /** 稳定代码 */
    private final String code;
}