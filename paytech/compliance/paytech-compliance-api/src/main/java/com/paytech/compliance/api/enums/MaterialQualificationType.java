package com.paytech.compliance.api.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** Client 入驻材料查询允许的资格类型，不含其他角色资格。 */
public enum MaterialQualificationType implements CodeEnum {
    INDIVIDUAL_KYC("INDIVIDUAL_KYC"),

    ORGANIZATION_KYB("ORGANIZATION_KYB"),

    RELATED_PERSON_KYC("RELATED_PERSON_KYC");
    private final String code;
    /** 固定发布代码，不依赖 Java 枚举名称。 */
    MaterialQualificationType(String code) { this.code=code; }
    /** 返回稳定业务代码。 */
    @Override public String getCode() { return code; }
    /** 消费方显式拒绝未知资格类型。 */
    public static MaterialQualificationType fromCode(String code) {
        for (var value:values()) if (value.code.equals(code)) return value;
        throw new IllegalArgumentException("未知材料资格类型");
    }
}
