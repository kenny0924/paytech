package com.paytech.payment.routing.domain;

import java.math.BigDecimal;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

/** 路由候选：调用方从 Channel 候选查询转换而来的 MID 三元组与本次匹配到的能力。 */
@Getter
@Setter
public class RouteCandidate {
    /** 渠道 ID */
    private Long channelId;

    /** 渠道账号 ID */
    private Long channelAccountId;

    /** MID ID */
    private Long midId;

    /**
     * 明确支持的动作
     * <li>能力代码去掉 ACTION. 前缀后的动作，例如 PAY、AUTHORIZE。</li>
     */
    private Set<String> supportedActions;

    /** 本次币种匹配到的金额下限，未声明为 null */
    private BigDecimal minAmount;

    /** 本次币种匹配到的金额上限，未声明为 null */
    private BigDecimal maxAmount;
}
