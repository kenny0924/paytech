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
}
