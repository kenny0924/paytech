package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionReleaseStatus implements CodeEnum {
    DRAFT("DRAFT"),

    VALIDATING("VALIDATING"),

    SHADOWING("SHADOWING"),

    REVIEW_PENDING("REVIEW_PENDING"),

    APPROVED("APPROVED"),

    ACTIVE("ACTIVE"),

    SUPERSEDED("SUPERSEDED"),

    REJECTED("REJECTED");

    private final String code;

    DecisionReleaseStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
