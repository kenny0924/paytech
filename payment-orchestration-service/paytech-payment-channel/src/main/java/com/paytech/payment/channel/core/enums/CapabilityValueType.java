package com.paytech.payment.channel.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 能力明细的封闭值类型。 */
@Getter
@AllArgsConstructor
public enum CapabilityValueType implements CodeEnum {
    /** BOOLEAN 对应的稳定业务代码。 */
    BOOLEAN("BOOLEAN"),

    /** DECIMAL 对应的稳定业务代码。 */
    DECIMAL("DECIMAL"),

    /** CODE 对应的稳定业务代码。 */
    CODE("CODE");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
