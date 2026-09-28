package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum MergePolicy implements CodeEnum {
    FIRST("FIRST"),

    LAST("LAST"),

    MOST_RESTRICTIVE("MOST_RESTRICTIVE"),

    UNION("UNION"),

    MIN("MIN"),

    MAX("MAX");

    private final String code;

    MergePolicy(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
