package com.paytech.billing.core.constant;

/** Billing 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key。 */
public final class BillingLocks {

    /** 纯静态常量。 */
    private BillingLocks() {
    }

    /** 同一计费事实的批准与作废入口共用的资源锁。 */
    public static String billingFact(long factId) {
        return "billing-fact:" + factId;
    }

    /** 同一来源引用（提交的业务唯一身份）的提交入口使用的资源锁；来源代码与引用均已按安全格式校验。 */
    public static String billingFactSource(String sourceSystem, String sourceReference) {
        return "billing-fact-source:" + sourceSystem + ":" + sourceReference;
    }
}
