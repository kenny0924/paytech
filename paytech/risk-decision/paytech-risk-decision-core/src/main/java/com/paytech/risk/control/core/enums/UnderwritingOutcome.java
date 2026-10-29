package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 承保提交或复核给出的结论。 */
public enum UnderwritingOutcome implements CodeEnum {
    /** 批准；HIGH 风险提交时表示提交复核。 */
    APPROVE("APPROVE"),

    /** 拒绝，须写原因。 */
    DECLINE("DECLINE");

    private final String code;

    UnderwritingOutcome(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
