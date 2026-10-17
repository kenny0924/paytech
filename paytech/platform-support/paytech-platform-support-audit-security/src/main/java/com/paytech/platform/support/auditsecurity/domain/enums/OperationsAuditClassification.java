package com.paytech.platform.support.auditsecurity.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 审计属性的存储分类；未知分类不得降级为公开文本。 */
public enum OperationsAuditClassification implements CodeEnum {
    PUBLIC("PUBLIC"),

    INTERNAL("INTERNAL"),

    CONFIDENTIAL("CONFIDENTIAL"),

    RESTRICTED("RESTRICTED");

    private final String code;

    OperationsAuditClassification(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
