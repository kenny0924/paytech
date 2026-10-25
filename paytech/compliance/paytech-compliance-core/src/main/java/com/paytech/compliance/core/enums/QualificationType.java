package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 角色资格投影使用的资格类型（t_role_qualification.qualification_type）；关联人员 KYC 不形成角色资格。 */
public enum QualificationType implements CodeEnum {
    INDIVIDUAL_KYC("INDIVIDUAL_KYC"),

    ORGANIZATION_KYB("ORGANIZATION_KYB"),

    PARTNER_DUE_DILIGENCE("PARTNER_DUE_DILIGENCE"),

    ENTITY_UNDERWRITING("ENTITY_UNDERWRITING");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    QualificationType(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按代码查找，未知代码返回 null 由调用方 Fail-Closed。 */
    public static QualificationType ofCode(String code) {
        for (QualificationType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
