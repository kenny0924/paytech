package com.paytech.channel.connector.domain;

import java.time.Instant;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次技术账号写操作的上下文：账号、所属渠道、本次变更记录与写入时间。 */
@Getter
@AllArgsConstructor
public class ChannelAccountContext {
    /** 被写入的账号；新建时为待插入对象。 */
    private final ChannelAccount account;

    /** 账号所属渠道。 */
    private final PaymentChannel channel;

    /** 与本次写入同事务插入的变更记录。 */
    private final ChannelConfigChangeRecord change;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;

    /** 账号是否已写入参数与凭据。 */
    public boolean configured() {
        return account.getAccountVaultToken() != null;
    }

    /** 激活前置：所属渠道 ACTIVE 且账号已配置。 */
    public boolean canActivate() {
        return channel.getChannelStatus() == ChannelStatus.ACTIVE && configured();
    }

    /** 账号是否已关闭；关闭后拒绝一切写入。 */
    public boolean closed() {
        return account.getAccountStatus() == ChannelStatus.CLOSED;
    }
}
