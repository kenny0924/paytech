package com.paytech.billing.core.constant;

/** Billing 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key。 */
public final class BillingLocks {

    private BillingLocks() {
    }

    /** 同一渠道成本方案的创建与版本创建、提交、批准、驳回共用的资源锁。 */
    public static String channelCostPlan(String planCode) {
        return "channel-cost-plan:" + planCode;
    }
}
