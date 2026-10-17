package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Hosted Checkout Session 的闭合生命周期状态。 */
public enum CheckoutSessionStatus implements CodeEnum {
    PREPARING("PREPARING"),

    OPEN("OPEN"),

    PAYMENT_PROCESSING("PAYMENT_PROCESSING"),

    COMPLETED("COMPLETED"),

    CANCELLED("CANCELLED"),

    EXPIRED("EXPIRED");

    private final String code;

    CheckoutSessionStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
