package com.paytech.channel.connector.core.constant;

import java.time.Duration;

import com.paytech.channel.connector.protocol.core.constant.ChannelLocks;

/** channel-connector 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key。 */
public final class ChannelConnectorLocks {

    private ChannelConnectorLocks() {
    }

    /** 同一渠道成本方案的创建、版本创建、提交、批准、驳回与到期切换共用的资源锁。 */
    public static String channelCostPlan(String planCode) {
        return "channel-cost-plan:" + planCode;
    }

    // ---- CC-B 追加：渠道 / 技术账号 / MID 管理写入口 ----

    /** 渠道配置写入口统一的锁等待预算，与 protocol {@link ChannelLocks#CHANNEL_CONFIG_WAIT} 相同。 */
    public static final Duration CHANNEL_CONFIG_WAIT = ChannelLocks.CHANNEL_CONFIG_WAIT;

    /** 渠道配置写入口统一的有限锁租约，与 protocol {@link ChannelLocks#CHANNEL_CONFIG_LEASE} 相同。 */
    public static final Duration CHANNEL_CONFIG_LEASE = ChannelLocks.CHANNEL_CONFIG_LEASE;

    /** 渠道资源锁；渠道身份归 protocol，Key 由其 {@link ChannelLocks#channel(long)} 定义。 */
    public static String channel(long channelId) {
        return ChannelLocks.channel(channelId);
    }

    /** 技术账号的更新、配置写入、状态变更，以及改绑到该账号的 MID 共用的资源锁。 */
    public static String channelAccount(long channelAccountId) {
        return "channel-account:" + channelAccountId;
    }

    /** MID 的改绑、状态变更及其能力组创建、明细替换、启停共用的资源锁。 */
    public static String channelMid(long midId) {
        return "channel-mid:" + midId;
    }
}
