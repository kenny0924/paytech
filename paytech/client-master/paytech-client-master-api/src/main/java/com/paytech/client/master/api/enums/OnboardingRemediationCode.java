package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 需求清单项的补救动作；前端按 onboardingSchema.remediation.<CODE> 展示。 */
public enum OnboardingRemediationCode implements CodeEnum {
    /** 补充信息。 */
    PROVIDE_FIELD,

    /** 上传材料。 */
    UPLOAD_DOCUMENT,

    /** 添加人员。 */
    ADD_PERSON,

    /** 添加关系。 */
    ADD_RELATION,

    /** 确认声明。 */
    SIGN_DECLARATION,

    /** 联系客服。 */
    CONTACT_SUPPORT,

    /** 无需操作。 */
    NONE;

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
    public static OnboardingRemediationCode fromCode(String code) {
        for (OnboardingRemediationCode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
