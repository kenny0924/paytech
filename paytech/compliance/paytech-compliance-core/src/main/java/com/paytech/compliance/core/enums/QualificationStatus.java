package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 角色资格投影与资格决定的结论（t_role_qualification.status、t_qualification_decision.outcome）。 */
public enum QualificationStatus implements CodeEnum {
    APPROVED("APPROVED"),

    REJECTED("REJECTED"),

    REVOKED("REVOKED"),

    EXPIRED("EXPIRED");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    QualificationStatus(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 投影进入该状态时的复审状态
     * <li>本期不安排持续复审：APPROVED 为 NOT_REQUIRED，其余终态为 COMPLETED（设计约束 ck_role_qual_review 由应用保证）。</li>
     */
    public QualificationReviewStatus reviewStatus() {
        return this == APPROVED ? QualificationReviewStatus.NOT_REQUIRED : QualificationReviewStatus.COMPLETED;
    }

    /** 按代码查找，未知代码返回 null 由调用方 Fail-Closed。 */
    public static QualificationStatus ofCode(String code) {
        for (QualificationStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
