package com.paytech.channel.connector.domain;

import java.time.Instant;
import java.util.Objects;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次 MID 写操作的上下文：MID、所属渠道、相关账号、本次变更记录与写入时间
 * <li>创建与状态变更时 account 为 MID 当前绑定账号；改绑时为目标账号。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelMidContext {
    /** 被写入的 MID；新建时为待插入对象。 */
    private final ChannelMid mid;

    /** MID 所属渠道。 */
    private final PaymentChannel channel;

    /** 当前绑定账号，或改绑的目标账号。 */
    private final ChannelAccount account;

    /** 与本次写入同事务插入的变更记录。 */
    private final ChannelConfigChange change;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;

    /** 账号可被本 MID 绑定：同渠道、同 OE 且未关闭。 */
    public boolean accountBindable() {
        return Objects.equals(account.getChannelId(), mid.getChannelId())
            && Objects.equals(account.getEntityNo(), mid.getEntityNo())
            && account.getAccountStatus() != ChannelStatus.CLOSED;
    }

    /** 激活前置：渠道与当前账号均 ACTIVE。 */
    public boolean canActivate() {
        return channel.getChannelStatus() == ChannelStatus.ACTIVE
            && account.getAccountStatus() == ChannelStatus.ACTIVE;
    }

    /** 目标状态下的首次激活时间：已有则保留，首次进入 ACTIVE 时为本次写入时间。 */
    public Instant activatedAtAfter(ChannelStatus target) {
        if (mid.getActivatedAt() != null || target != ChannelStatus.ACTIVE) {
            return mid.getActivatedAt();
        }
        return now;
    }

    /** 目标状态下的关闭时间：进入 CLOSED 时为本次写入时间，否则保持原值。 */
    public Instant closedAtAfter(ChannelStatus target) {
        return target == ChannelStatus.CLOSED ? now : mid.getClosedAt();
    }

    /** MID 是否已关闭；关闭后拒绝一切写入。 */
    public boolean closed() {
        return mid.getMidStatus() == ChannelStatus.CLOSED;
    }
}
