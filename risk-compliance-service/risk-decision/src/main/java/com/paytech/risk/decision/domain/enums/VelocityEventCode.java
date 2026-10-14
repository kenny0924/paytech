package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 实时累计事件编码。 */
public enum VelocityEventCode implements CodeEnum {
    /** 一次成功完成的正式支付尝试评估。 */
    PAYMENT_ATTEMPT_ASSESSED("PAYMENT_ATTEMPT_ASSESSED"),

    /** 正式评估结论为审核或拒绝的支付尝试。 */
    PAYMENT_ATTEMPT_REJECTED("PAYMENT_ATTEMPT_REJECTED");

    private final String code;

    VelocityEventCode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
