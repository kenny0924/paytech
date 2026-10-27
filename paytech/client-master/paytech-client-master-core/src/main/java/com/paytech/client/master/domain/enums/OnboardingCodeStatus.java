package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** PayFac 入驻码状态。 */
public enum OnboardingCodeStatus implements CodeEnum {
    /** 可用。 */
    ACTIVE,

    /** 已作废，终态。 */
    REVOKED,

    /** 已过期或用尽，终态。 */
    EXPIRED;

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
    public static OnboardingCodeStatus fromCode(String code) {
        for (OnboardingCodeStatus value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
