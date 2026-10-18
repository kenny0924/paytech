package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 同源出口的配置类型。 */
public enum FlowEdgeType implements CodeEnum {
    UNCONDITIONAL("UNCONDITIONAL"),

    CONDITIONAL("CONDITIONAL"),

    ELSE("ELSE");

    private final String code;

    FlowEdgeType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
