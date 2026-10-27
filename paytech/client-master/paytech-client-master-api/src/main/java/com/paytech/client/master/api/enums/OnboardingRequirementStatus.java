package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 入驻需求清单项状态。 */
public enum OnboardingRequirementStatus implements CodeEnum {
    /** 提交前必须满足。 */
    CURRENTLY_DUE,

    /** 批准前必须满足，允许先提交。 */
    EVENTUALLY_DUE,

    /** 已超过退回给出的补件期限。 */
    PAST_DUE,

    /** 已提供，等待审核核验。 */
    PENDING_VERIFICATION,

    /** 已满足。 */
    SATISFIED,

    /** 审核已豁免。 */
    WAIVED;

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
    public static OnboardingRequirementStatus fromCode(String code) {
        for (OnboardingRequirementStatus value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
