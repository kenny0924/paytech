package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 入驻需求级错误码；前端按 onboardingSchema.error.<CODE> 展示文案。 */
public enum OnboardingRequirementCode implements CodeEnum {
    /** 字段未填写。 */
    FIELD_MISSING,

    /** 字段格式不正确。 */
    FIELD_FORMAT_INVALID,

    /** 字段取值不在目录。 */
    FIELD_VALUE_NOT_IN_CATALOG,

    /** 未达到辖区最低年龄。 */
    DOB_AGE_UNDER_MINIMUM,

    /** 标识号码格式不符。 */
    IDENTIFIER_FORMAT_INVALID,

    /** 标识号码校验位不正确。 */
    IDENTIFIER_CHECKSUM_FAILED,

    /** 证件已过期。 */
    IDENTIFIER_EXPIRED,

    /** 号码已绑定其他主体。 */
    IDENTIFIER_CONFLICT,

    /** 缺少最终受益人或兜底控制人。 */
    UBO_MISSING,

    /** 受益人持股合计超过 100%。 */
    UBO_OWNERSHIP_EXCEEDS_100,

    /** 签字人不足。 */
    SIGNATORY_MISSING,

    /** 缺少董事或经理。 */
    DIRECTOR_MISSING,

    /** 缺少经理。 */
    MANAGER_MISSING,

    /** 缺少控制人。 */
    CONTROL_PERSON_MISSING,

    /** 组织未添加关联人员。 */
    RELATED_PERSON_INCOMPLETE,

    /** 缺少材料。 */
    DOCUMENT_MISSING,

    /** 材料已过期。 */
    DOCUMENT_EXPIRED,

    /** 材料超出时效。 */
    DOCUMENT_TOO_OLD,

    /** 材料文件不可用。 */
    DOCUMENT_UNREADABLE,

    /** 材料类型不被接受。 */
    DOCUMENT_TYPE_INVALID,

    /** 材料所属人员已移除。 */
    DOCUMENT_SUBJECT_REMOVED,

    /** 声明未确认。 */
    DECLARATION_MISSING;

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
    public static OnboardingRequirementCode fromCode(String code) {
        for (OnboardingRequirementCode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
