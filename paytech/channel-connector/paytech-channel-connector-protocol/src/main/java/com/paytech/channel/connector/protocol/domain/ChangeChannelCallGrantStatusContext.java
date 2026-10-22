package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次调用授权启停的上下文：已加载的授权、目标状态、操作者、本次变更记录与写入时间。 */
@Getter
@AllArgsConstructor
public class ChangeChannelCallGrantStatusContext {
    /** 被启停的授权，SQL 成功后同步状态、版本与更新时间。 */
    private final ChannelCallGrant grant;

    /** 目标状态。 */
    private final ChannelCallGrantStatus targetStatus;

    /** 操作者 Account ID。 */
    private final long actorId;

    /** 与本次写入同事务插入的变更记录。 */
    private final ChannelConfigChangeRecord change;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;
}
