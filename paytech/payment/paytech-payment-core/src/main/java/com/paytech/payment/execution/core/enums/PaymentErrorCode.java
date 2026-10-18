package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 上游据此区分需要重新准备工具的原因，不能当作渠道拒付。 */
@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements CodeMessageEnum {
    PAYMENT_INSTRUMENT_EXPIRED("PAYMENT_INSTRUMENT_EXPIRED", "支付工具已过期"),

    PAYMENT_INSTRUMENT_REVOKED("PAYMENT_INSTRUMENT_REVOKED", "支付工具已撤销");

    /** 稳定代码 */
    private final String code;

    /** 安全错误说明 */
    private final String message;
}