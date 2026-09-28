package com.paytech.payment.channel.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道、账号和 MID 的静态生命周期。 */
@Getter
@AllArgsConstructor
public enum ChannelStatus implements CodeEnum {
    /** DRAFT 对应的稳定业务代码。 */
    DRAFT("DRAFT"),

    /** ACTIVE 对应的稳定业务代码。 */
    ACTIVE("ACTIVE"),

    /** SUSPENDED 对应的稳定业务代码。 */
    SUSPENDED("SUSPENDED"),

    /** CLOSED 对应的稳定业务代码。 */
    CLOSED("CLOSED");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
