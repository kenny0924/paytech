package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 支付渠道详情。 */
@Getter
@Setter
public class ChannelResponseBody {
    /** 渠道 ID，十进制字符串 */
    private String channelId;

    /** 渠道编码，不可修改 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 渠道状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /** 创建时间，UTC */
    private Instant createTime;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
