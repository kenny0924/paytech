package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

import lombok.Getter;
import lombok.Setter;

/** 渠道列表项中由渠道身份与调用授权得出的部分；技术账号与 MID 计数由支付扩展（core）补齐。 */
@Getter
@Setter
public class ChannelSummary {
    /** 渠道内部标识。 */
    private Long channelId;

    /** 渠道编码。 */
    private String channelCode;

    /** 渠道名称。 */
    private String channelName;

    /** 渠道类型。 */
    private ChannelType channelType;

    /** 渠道状态。 */
    private ChannelStatus channelStatus;

    /** 已配置的出站地址数；未配置为 0。 */
    private int endpointCount;

    /** ACTIVE 调用授权数；只对服务渠道统计，支付渠道为 null。 */
    private Long activeGrantCount;

    /** 最后变更 UTC 时间。 */
    private Instant updateTime;
}
