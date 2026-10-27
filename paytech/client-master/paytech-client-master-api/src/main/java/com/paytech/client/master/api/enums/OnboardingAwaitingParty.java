package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 需求清单项等待的行动方。 */
public enum OnboardingAwaitingParty implements CodeEnum {
    /** Client。 */
    CLIENT,

    /** 审核或运营。 */
    OPERATIONS,

    /** 系统。 */
    SYSTEM;

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
    public static OnboardingAwaitingParty fromCode(String code) {
        for (OnboardingAwaitingParty value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
