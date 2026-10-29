package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 承保结论来源。 */
public enum UnderwritingDecisionSource implements CodeEnum {
    /** 人工承保或复核。 */
    MANUAL("MANUAL"),

    /** 按自动承保规则生成。 */
    AUTO_RULE("AUTO_RULE");

    private final String code;

    UnderwritingDecisionSource(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
