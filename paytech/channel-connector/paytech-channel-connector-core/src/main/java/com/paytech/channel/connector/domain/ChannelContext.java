package com.paytech.channel.connector.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道写操作的上下文：渠道、本次变更记录与写入时间；只含已加载事实上的纯判断。 */
@Getter
@AllArgsConstructor
public class ChannelContext {
    /** 被写入的渠道；新建时为待插入对象。 */
    private final ChannelIdentity channel;

    /** 与本次写入同事务插入的变更记录。 */
    private final ChannelConfigChangeRecord change;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;

    /**
     * 渠道可否关闭
     *
     * @param openAccounts 渠道下未关闭的账号数
     * @param openMids 渠道下未关闭的 MID 数
     * @return 两者均为零时可关闭
     */
    public static boolean canClose(long openAccounts, long openMids) {
        return openAccounts == 0 && openMids == 0;
    }

    /** 渠道是否已关闭；关闭后拒绝一切写入。 */
    public boolean closed() {
        return channel.getChannelStatus() == ChannelStatus.CLOSED;
    }
}
