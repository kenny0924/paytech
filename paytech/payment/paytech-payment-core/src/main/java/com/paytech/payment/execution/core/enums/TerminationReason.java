package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付意图终止原因，决定安全终止后的 Intent 终态。 */
@Getter
@AllArgsConstructor
public enum TerminationReason implements CodeEnum {
    /** 取消订单，终态 CANCELLED */
    ORDER_CANCELLED("ORDER_CANCELLED", IntentStatus.CANCELLED),

    /** 经营主体关闭，终态 CANCELLED */
    OPERATING_ENTITY_CLOSED("OPERATING_ENTITY_CLOSED", IntentStatus.CANCELLED),

    /** 订单到期，终态 EXPIRED */
    ORDER_EXPIRED("ORDER_EXPIRED", IntentStatus.EXPIRED);

    /** 稳定代码 */
    private final String code;

    /** 安全终止后写入的 Intent 终态 */
    @Getter(AccessLevel.NONE)
    private final IntentStatus targetStatus;

    /** 安全终止后写入的 Intent 终态。 */
    public IntentStatus targetStatus() {
        return targetStatus;
    }
}
