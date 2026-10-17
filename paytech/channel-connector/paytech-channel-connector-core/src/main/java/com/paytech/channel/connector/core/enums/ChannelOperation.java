package com.paytech.payment.channel.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行配置校验的渠道操作，不代表已执行资金动作。 */
@Getter
@AllArgsConstructor
public enum ChannelOperation implements CodeEnum {
    /** PAY 对应的稳定业务代码。 */
    PAY("PAY"),

    /** AUTHORIZE 对应的稳定业务代码。 */
    AUTHORIZE("AUTHORIZE"),

    /** AUTHENTICATE 对应的稳定业务代码。 */
    AUTHENTICATE("AUTHENTICATE"),

    /** CAPTURE 对应的稳定业务代码。 */
    CAPTURE("CAPTURE"),

    /** VOID 对应的稳定业务代码。 */
    VOID("VOID"),

    /** REFUND 对应的稳定业务代码。 */
    REFUND("REFUND"),

    /** QUERY 对应的稳定业务代码。 */
    QUERY("QUERY");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
