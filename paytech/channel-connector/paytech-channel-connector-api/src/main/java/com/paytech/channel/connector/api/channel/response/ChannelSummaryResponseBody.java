package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 支付渠道列表项。 */
@Getter
@Setter
public class ChannelSummaryResponseBody {
    /** 渠道 ID，十进制字符串 */
    private String channelId;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 渠道状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /** 渠道下全部技术账号数（含已关闭） */
    private long accountCount;

    /** 渠道下全部 MID 数（含已关闭） */
    private long midCount;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
