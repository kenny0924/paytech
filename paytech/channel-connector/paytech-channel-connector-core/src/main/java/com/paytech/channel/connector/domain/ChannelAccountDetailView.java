package com.paytech.channel.connector.domain;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 技术账号详情：账号与当前绑定且未关闭的 MID 数的组合结果；Token 只在 ext 边界截取末 4 位。 */
@Getter
@AllArgsConstructor
public class ChannelAccountDetailView {
    /** 技术账号。 */
    private final ChannelAccount account;

    /** 当前绑定本账号且未关闭的 MID 数。 */
    private final long boundMidCount;
}
