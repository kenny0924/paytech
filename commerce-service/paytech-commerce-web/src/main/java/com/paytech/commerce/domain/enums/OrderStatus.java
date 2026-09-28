package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Commerce Order 的闭合生命周期状态。 */
public enum OrderStatus implements CodeEnum {
    CREATED("CREATED"),

    OPEN("OPEN"),

    PAID("PAID"),

    CANCELLED("CANCELLED"),

    EXPIRED("EXPIRED");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
