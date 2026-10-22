package com.paytech.payment.routing.domain;

import lombok.Getter;
import lombok.Setter;

/** 支付路由结果 */
@Getter
@Setter
public class RouteResult {
    /** 支付渠道 ID */
    private Long channelId;

    /** 渠道账户 ID */
    private Long channelAccountId;

    /** MID ID */
    private Long midId;

    /** 渠道编码，只用于本次请求拼渠道支付通知地址，不落库 */
    private String channelCode;

    /** 选中 MID 的 3DS 模式，只用于本次请求的 PAY 前能力检查，不落库；未声明为 null */
    private String threeDsMode;
}
