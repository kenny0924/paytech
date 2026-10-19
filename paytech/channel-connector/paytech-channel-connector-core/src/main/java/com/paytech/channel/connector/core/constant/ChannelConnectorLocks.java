package com.paytech.channel.connector.core.constant;

/** channel-connector 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key。 */
public final class ChannelConnectorLocks {

    private ChannelConnectorLocks() {
    }

    /** 同一渠道成本方案的创建、版本创建、提交、批准、驳回与到期切换共用的资源锁。 */
    public static String channelCostPlan(String planCode) {
        return "channel-cost-plan:" + planCode;
    }
}
