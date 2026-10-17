package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 决策流管理状态，不代表发布状态。 */
public enum FlowStatus implements CodeEnum {
    CONFIGURABLE("CONFIGURABLE"),

    DELETED("DELETED");

    private final String code;

    FlowStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
