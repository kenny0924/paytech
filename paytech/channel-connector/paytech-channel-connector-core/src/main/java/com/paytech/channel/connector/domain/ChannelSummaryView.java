package com.paytech.channel.connector.domain;

import java.time.Instant;

import com.paytech.channel.connector.core.enums.ChannelStatus;

import lombok.Getter;
import lombok.Setter;

/** 渠道列表项：渠道行与同库账号、MID 计数的组合查询结果。 */
@Getter
@Setter
public class ChannelSummaryView {
    /** 渠道 ID。 */
    private Long channelId;

    /** 渠道编码。 */
    private String channelCode;

    /** 渠道名称。 */
    private String channelName;

    /** 渠道状态。 */
    private ChannelStatus channelStatus;

    /** 渠道下全部账号数（含已关闭）。 */
    private long accountCount;

    /** 渠道下全部 MID 数（含已关闭）。 */
    private long midCount;

    /** 最后修改 UTC 时间。 */
    private Instant updateTime;
}
