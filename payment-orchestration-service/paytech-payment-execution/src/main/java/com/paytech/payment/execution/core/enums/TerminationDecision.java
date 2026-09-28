package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付意图终止决定 */
@Getter
@AllArgsConstructor
public enum TerminationDecision implements CodeEnum {
    /** 本次可以安全终止 */
    TERMINATED("TERMINATED"),

    /** 此前已取消或过期 */
    ALREADY_TERMINATED("ALREADY_TERMINATED"),

    /** 存在未决尝试 */
    PENDING("PENDING"),

    /** 已有未释放授权 */
    REVERSAL_REQUIRED("REVERSAL_REQUIRED"),

    /** 已收款 */
    ALREADY_PAID("ALREADY_PAID");

    /** 稳定代码 */
    private final String code;
}
