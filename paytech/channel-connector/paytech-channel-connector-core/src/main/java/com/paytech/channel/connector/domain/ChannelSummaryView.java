package com.paytech.channel.connector.domain;

import com.paytech.channel.connector.protocol.domain.ChannelSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道列表项：protocol 的渠道摘要（含出站地址数与 ACTIVE 授权数）与本模块账号、MID 计数的组合。 */
@Getter
@AllArgsConstructor
public class ChannelSummaryView {
    /** 渠道摘要。 */
    private final ChannelSummary channel;

    /** 渠道下全部账号数（含已关闭）。 */
    private final long accountCount;

    /** 渠道下全部 MID 数（含已关闭）。 */
    private final long midCount;
}
