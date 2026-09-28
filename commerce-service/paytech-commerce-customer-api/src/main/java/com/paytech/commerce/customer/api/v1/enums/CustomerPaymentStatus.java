package com.paytech.commerce.customer.api.v1.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Customer 原支付提交的稳定状态 Code；HTTP 字段仍为 String。 */
public enum CustomerPaymentStatus implements CodeEnum {
    AWAITING_CARD("AWAITING_CARD"),

    PROCESSING("PROCESSING"),

    REQUIRES_ACTION("REQUIRES_ACTION"),

    AUTHORIZED("AUTHORIZED"),

    UNKNOWN("UNKNOWN"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED"),

    CANCELLED("CANCELLED"),

    VOIDED("VOIDED"),

    EXPIRED("EXPIRED");

    private final String code;

    /** 冻结稳定 Wire Code，与 Java 常量名称解耦。 */
    CustomerPaymentStatus(String code) { this.code = code; }

    /** 返回稳定协议值。 */
    public String getCode() { return code; }
}
