package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 单笔 Order 资金成熟的闭合投影状态。 */
public enum FundsAvailabilityStatus implements CodeEnum {
    NOT_APPLICABLE("NOT_APPLICABLE"),

    PENDING("PENDING"),

    AVAILABLE("AVAILABLE"),

    FULLY_OFFSET("FULLY_OFFSET");

    private final String code;

    FundsAvailabilityStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
