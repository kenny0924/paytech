package com.paytech.commercial.service.processor;

/**
 * 写入 Processor 的资源锁 Key（technique.md §2.2）；同一资源的全部入口共用同一 Key
 * <li>LockCommand 单资源工厂会再加 {@code lock:} 前缀，Redis Namespace 由 redis.key-prefix 统一添加。</li>
 */
public final class CommercialLocks {
    /** 纯静态工具。 */
    private CommercialLocks() {
    }

    /** 合同创建：按业务唯一编号串行化。 */
    public static String agreementNo(String agreementNo) {
        return "agreement:" + agreementNo;
    }

    /** 合同聚合（签署、状态、订阅、开通项，以及 C 线的 Plan 创建）。 */
    public static String agreement(long agreementId) {
        return "agreement:" + agreementId;
    }

    /** 画像创建：按不可变代码串行化。 */
    public static String pricingProfileCode(String pricingProfileCode) {
        return "pricing-profile:" + pricingProfileCode;
    }

    /** 画像修改、激活与退役。 */
    public static String pricingProfile(long pricingProfileId) {
        return "pricing-profile:" + pricingProfileId;
    }
}
