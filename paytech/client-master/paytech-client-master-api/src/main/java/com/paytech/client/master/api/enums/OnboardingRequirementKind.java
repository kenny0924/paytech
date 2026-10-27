package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 入驻需求清单项类别。 */
public enum OnboardingRequirementKind implements CodeEnum {
    /** 资料字段。 */
    FIELD,

    /** 法律标识。 */
    IDENTIFIER,

    /** 关联人员。 */
    PERSON,

    /** 关系人门槛。 */
    RELATION,

    /** 材料。 */
    DOCUMENT,

    /** 声明。 */
    DECLARATION;

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应常量；不认识或为空时返回 null，由调用方按自身合同处理
     */
    public static OnboardingRequirementKind fromCode(String code) {
        for (OnboardingRequirementKind value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
