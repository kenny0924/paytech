package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 材料程序映射允许的资格类型
 * <li>与 risk-api 发布的同名 Code 一致；业务模块不依赖 API 模块，故在 Owner 内部声明。</li>
 */
public enum MaterialQualificationType implements CodeEnum {
    INDIVIDUAL_KYC("INDIVIDUAL_KYC"),

    ORGANIZATION_KYB("ORGANIZATION_KYB"),

    RELATED_PERSON_KYC("RELATED_PERSON_KYC");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    MaterialQualificationType(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按代码查找，未知代码返回 null 由调用方 Fail-Closed。 */
    public static MaterialQualificationType ofCode(String code) {
        for (MaterialQualificationType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
