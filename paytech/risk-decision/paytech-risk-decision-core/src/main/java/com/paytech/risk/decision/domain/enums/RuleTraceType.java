package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 规则轨迹行类型。 */
public enum RuleTraceType implements CodeEnum {
    /** 一条规则的命中结果。 */
    RULE("RULE"),

    /** 一个条件谓词的求值结果。 */
    PREDICATE("PREDICATE");

    private final String code;

    RuleTraceType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
