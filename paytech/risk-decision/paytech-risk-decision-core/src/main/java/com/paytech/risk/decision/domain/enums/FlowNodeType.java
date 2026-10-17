package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 配置节点的闭合类型。 */
public enum FlowNodeType implements CodeEnum {
    START("START"),

    RULE_SET("RULE_SET"),

    END("END");

    private final String code;

    FlowNodeType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
