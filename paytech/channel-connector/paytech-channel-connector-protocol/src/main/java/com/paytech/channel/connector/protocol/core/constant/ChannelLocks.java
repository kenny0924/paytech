package com.paytech.channel.connector.protocol.core.constant;

import java.time.Duration;

/**
 * 渠道身份与调用授权写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key
 * <li>渠道锁由 protocol 的渠道名称修改与 core 的渠道状态变更、账号创建、MID 创建、账号关闭共用，经 LockCommand.tryLocks 获取同一形态的 RLock。</li>
 */
public final class ChannelLocks {

    /**
     * 渠道配置写入口统一的锁等待预算
     * <li>渠道、账号、MID 三类资源存在多资源组合（账号关闭、MID 改绑），全部经 tryLocks 获取，单资源入口也用 tryLocks，
     * 避免单资源 SpinLock（lock: 前缀）与多资源 RLock 落在不同 Key 而失去互斥。</li>
     */
    public static final Duration CHANNEL_CONFIG_WAIT = Duration.ofSeconds(10);

    /**
     * 渠道配置写入口统一的有限锁租约
     * <li>须覆盖锁内最长外部调用（Vault Protect）与本地短事务；租约失效时由条件 SQL 兜底。</li>
     */
    public static final Duration CHANNEL_CONFIG_LEASE = Duration.ofSeconds(60);

    /** 常量类不实例化。 */
    private ChannelLocks() {
    }

    /** 渠道的名称修改、状态变更，以及其下账号创建、MID 创建与账号关闭共用的资源锁。 */
    public static String channel(long channelId) {
        return "channel:" + channelId;
    }

    /** 同一服务渠道的调用授权新建与启停共用的资源锁。 */
    public static String channelCallGrant(long channelId) {
        return "channel-call-grant:" + channelId;
    }
}
