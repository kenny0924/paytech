package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 余额桶移动的业务来源类型：决定唯一允许的转出桶、转入桶，以及能否经 {@code BalanceMovementApi} 发起
 * <li>同一来源以 {@code (sourceType, sourceRef)} 幂等；LOCK 只允许 AVAILABLE → LOCKED，RELEASE 只允许 LOCKED → AVAILABLE。</li>
 * <li>FACT_MATURITY 由 account 到期任务内部发起，接口请求使用它时拒绝（MOVEMENT_NOT_ALLOWED）。</li>
 */
public enum MovementSourceType implements CodeEnum {
    /** 经济事实到期：PENDING → AVAILABLE；sourceRef 为 {factId}:{factVersion}:{accountId}；仅内部 */
    FACT_MATURITY(BalanceBucket.PENDING, BalanceBucket.AVAILABLE, false),

    /** 出款锁定：AVAILABLE → LOCKED */
    PAYOUT_LOCK(BalanceBucket.AVAILABLE, BalanceBucket.LOCKED, true),

    /** 出款释放：LOCKED → AVAILABLE */
    PAYOUT_RELEASE(BalanceBucket.LOCKED, BalanceBucket.AVAILABLE, true),

    /** 缴税锁定：AVAILABLE → LOCKED */
    TAX_PAYMENT_LOCK(BalanceBucket.AVAILABLE, BalanceBucket.LOCKED, true),

    /** 缴税释放：LOCKED → AVAILABLE */
    TAX_PAYMENT_RELEASE(BalanceBucket.LOCKED, BalanceBucket.AVAILABLE, true);

    private final BalanceBucket fromBucket;

    private final BalanceBucket toBucket;

    private final boolean apiAllowed;

    /** 固定转出桶、转入桶与发起方式。 */
    MovementSourceType(BalanceBucket fromBucket, BalanceBucket toBucket, boolean apiAllowed) {
        this.fromBucket = fromBucket;
        this.toBucket = toBucket;
        this.apiAllowed = apiAllowed;
    }

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }

    /** 唯一允许的转出桶。 */
    public BalanceBucket getFromBucket() {
        return fromBucket;
    }

    /** 唯一允许的转入桶。 */
    public BalanceBucket getToBucket() {
        return toBucket;
    }

    /** 是否允许经 BalanceMovementApi 由外部调用方发起。 */
    public boolean isApiAllowed() {
        return apiAllowed;
    }

    /**
     * 判断一次移动的桶方向是否符合本来源类型
     *
     * @param from 请求的转出桶
     * @param to 请求的转入桶
     * @return 两者都与本类型固定方向一致时为真
     */
    public boolean allows(BalanceBucket from, BalanceBucket to) {
        return fromBucket == from && toBucket == to;
    }
}
