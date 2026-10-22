package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道名称与说明修改的上下文：已加载的渠道、新值、本次变更记录与写入时间。 */
@Getter
@AllArgsConstructor
public class UpdateChannelContext {
    /** 被修改的渠道，SQL 成功后同步名称、说明、版本与更新时间。 */
    private final Channel channel;

    /** 新名称。 */
    private final String channelName;

    /** 新说明，可空（整体替换）。 */
    private final String description;

    /** 操作者 Account ID。 */
    private final long actorId;

    /** 与本次写入同事务插入的变更记录。 */
    private final ChannelConfigChangeRecord change;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;
}
