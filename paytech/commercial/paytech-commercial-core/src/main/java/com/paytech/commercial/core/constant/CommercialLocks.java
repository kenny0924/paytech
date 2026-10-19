package com.paytech.commercial.core.constant;

/**
 * 商务写入的资源锁 Key（technique.md §2.2）；同一资源的全部写入口共用同一 Key
 * <li>Key 由已校验的十进制 ID 构造，模板统一添加 lock: 前缀与服务 Namespace。</li>
 */
public final class CommercialLocks {
    /** 纯静态常量。 */
    private CommercialLocks() {
    }

    /**
     * 合同资源锁：合同写入与在合同下创建条款方案共用。
     *
     * @param agreementId 合同 ID
     * @return 锁 Key
     */
    public static String agreement(long agreementId) {
        return "agreement:" + agreementId;
    }

    /**
     * 条款方案资源锁：方案下版本、价格集合与状态推进共用，激活作业同样获取。
     *
     * @param termPlanId 方案 ID
     * @return 锁 Key
     */
    public static String termPlan(long termPlanId) {
        return "term-plan:" + termPlanId;
    }
}
