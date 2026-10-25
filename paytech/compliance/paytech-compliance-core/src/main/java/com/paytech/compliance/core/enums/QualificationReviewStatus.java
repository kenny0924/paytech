package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 角色资格的持续复审状态（t_role_qualification.review_status）；本期只写 NOT_REQUIRED 与 COMPLETED。 */
public enum QualificationReviewStatus implements CodeEnum {
    NOT_REQUIRED("NOT_REQUIRED"),

    ACTIVE("ACTIVE"),

    PAUSED("PAUSED"),

    COMPLETED("COMPLETED");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    QualificationReviewStatus(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
