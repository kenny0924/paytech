package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道出站地址整组替换的上下文：已锁定的渠道、规范化后的地址 JSON、操作者与写入时间。 */
@Getter
@AllArgsConstructor
public class UpdateChannelEndpointsContext {
    /** 被修改的渠道（事务内已锁定），SQL 成功后同步地址、版本与更新时间。 */
    private final Channel channel;

    /** 规范化后的出站地址 JSON 数组。 */
    private final String endpointsJson;

    /** 操作者 Account ID。 */
    private final long actorId;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;
}
