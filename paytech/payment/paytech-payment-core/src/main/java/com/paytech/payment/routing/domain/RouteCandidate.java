package com.paytech.payment.routing.domain;

import java.math.BigDecimal;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

/** 路由候选：由 channel-connector 候选查询转换而来的 MID 三元组与 channel-connector 已解析的能力。 */
@Getter
@Setter
public class RouteCandidate {
    /** 渠道 ID */
    private Long channelId;

    /** 渠道编码，用于拼渠道支付通知地址 */
    private String channelCode;

    /** 渠道账号 ID */
    private Long channelAccountId;

    /** MID ID */
    private Long midId;

    /**
     * 明确支持的渠道操作代码
     * <li>channel-connector 解析能力代码后返回的 ChannelOperation 代码，例如 PAY、AUTHORIZE；Payment 不解析能力代码。</li>
     */
    private Set<String> supportedActions;

    /** 本次币种匹配到的金额下限，未声明为 null */
    private BigDecimal minAmount;

    /** 本次币种匹配到的金额上限，未声明为 null */
    private BigDecimal maxAmount;

    /**
     * 3DS 模式（SECURITY.3DS_MODE）
     * <li>REQUIRED、SUPPORTED、UNSUPPORTED，取值常量见 ChannelCapabilityCodes；未声明为 null。</li>
     */
    private String threeDsMode;
}
