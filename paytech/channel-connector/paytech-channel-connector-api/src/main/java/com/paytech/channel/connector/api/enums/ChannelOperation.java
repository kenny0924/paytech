package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行配置校验的渠道操作，不代表已执行资金动作。 */
@Getter
@AllArgsConstructor
public enum ChannelOperation implements CodeEnum {
    /** PAY 对应的稳定业务代码。 */
    PAY("PAY", "PAY"),

    /** AUTHORIZE 对应的稳定业务代码。 */
    AUTHORIZE("AUTHORIZE", "AUTHORIZE"),

    /** AUTHENTICATE 对应的稳定业务代码。 */
    AUTHENTICATE("AUTHENTICATE", "AUTHENTICATE"),

    /** CAPTURE 对应的稳定业务代码。 */
    CAPTURE("CAPTURE", "CAPTURE"),

    /** VOID 对应的稳定业务代码。 */
    VOID("VOID", "VOID"),

    /** REFUND 对应的稳定业务代码。 */
    REFUND("REFUND", "REFUND"),

    /** QUERY 对应的稳定业务代码；只用于原交易（ORIGINAL_TRANSACTION）查单，执行标准 API PAY_QUERY。 */
    QUERY("QUERY", "PAY_QUERY");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;

    /**
     * 执行时对应的 Platform 标准 API 编码
     * <li>除 QUERY → PAY_QUERY（3DS 全链路 technique 3.3.2）外与操作代码相同。</li>
     */
    private final String standardApiCode;
}
