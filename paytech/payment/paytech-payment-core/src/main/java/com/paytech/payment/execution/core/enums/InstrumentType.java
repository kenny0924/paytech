package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** InstrumentType 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum InstrumentType implements CodeEnum {
    CARD("CARD");

    /** 稳定代码 */
    private final String code;
}
