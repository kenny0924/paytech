package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 辖区准入策略（rule_code=ACCEPTANCE_POLICY 的取值）。 */
public enum AcceptancePolicy implements CodeEnum {
    /** 开放。 */
    OPEN,

    /** 只按 ENHANCED 档受理。 */
    ENHANCED_ONLY,

    /** 不受理新申请。 */
    CLOSED;

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
    public static AcceptancePolicy fromCode(String code) {
        for (AcceptancePolicy value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
