package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道类型；创建后不可修改。 */
@Getter
@AllArgsConstructor
public enum ChannelType implements CodeEnum {
    /** 支付渠道：技术账号绑定 OE，有 MID、能力与成本，只经支付入口执行。 */
    PAYMENT("PAYMENT"),

    /** 服务渠道：最多一个不绑定 OE 的 ACTIVE 账号（凭据），只经通用执行入口并按调用授权执行。 */
    SERVICE("SERVICE");

    /** 数据库、HTTP 与业务比较使用的稳定代码。 */
    private final String code;
}
