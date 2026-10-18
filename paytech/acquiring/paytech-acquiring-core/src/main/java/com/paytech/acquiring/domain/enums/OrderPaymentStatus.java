package com.paytech.acquiring.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 根据本地 Intent 和最近 Attempt 事实得到的订单支付状态。 */
@Getter
@AllArgsConstructor
public enum OrderPaymentStatus implements CodeEnum {
    /** 尚未开始支付尝试。 */
    NOT_STARTED("NOT_STARTED"),

    /** 支付正在处理或结果尚未确定。 */
    PROCESSING("PROCESSING"),

    /** 订单支付成功。 */
    SUCCEEDED("SUCCEEDED"),

    /** 最近一次支付明确失败。 */
    FAILED("FAILED"),

    /** 支付已取消。 */
    CANCELLED("CANCELLED"),

    /** 支付机会已到期。 */
    EXPIRED("EXPIRED");

    /** 对外稳定状态代码。 */
    private final String code;
}
