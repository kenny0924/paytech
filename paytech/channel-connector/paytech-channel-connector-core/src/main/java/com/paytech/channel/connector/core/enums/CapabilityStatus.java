package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 能力条件组启停状态。 */
@Getter
@AllArgsConstructor
public enum CapabilityStatus implements CodeEnum {
    /** ENABLED 对应的稳定业务代码。 */
    ENABLED("ENABLED"),

    /** DISABLED 对应的稳定业务代码。 */
    DISABLED("DISABLED");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
