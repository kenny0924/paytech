package com.paytech.channel.connector.core.constant;

import java.time.Duration;

/** channel-connector 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key。 */
public final class ChannelConnectorLocks {

    private ChannelConnectorLocks() {
    }

    /** 同一渠道成本方案的创建、版本创建、提交、批准、驳回与到期切换共用的资源锁。 */
    public static String channelCostPlan(String planCode) {
        return "channel-cost-plan:" + planCode;
    }

    // ---- CC-B 追加：渠道 / 技术账号 / MID 管理写入口 ----

    /**
     * 渠道配置写入口统一的锁等待预算
     * <li>渠道、账号、MID 三类资源存在多资源组合（账号关闭、MID 改绑），全部经 LockCommand.tryLocks 获取同一形态的锁，
     * 单资源入口也用 tryLocks，避免单资源 SpinLock（lock: 前缀）与多资源 RLock 落在不同 Key 而失去互斥。</li>
     */
    public static final Duration CHANNEL_CONFIG_WAIT = Duration.ofSeconds(10);

    /**
     * 渠道配置写入口统一的有限锁租约
     * <li>须覆盖锁内最长外部调用（Platform 接入方查询、Vault Protect）与本地短事务；租约失效时由条件 SQL 兜底。</li>
     */
    public static final Duration CHANNEL_CONFIG_LEASE = Duration.ofSeconds(60);

    /** 渠道的更新、状态变更，以及其下账号创建、MID 创建与账号关闭共用的资源锁。 */
    public static String channel(long channelId) {
        return "channel:" + channelId;
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
