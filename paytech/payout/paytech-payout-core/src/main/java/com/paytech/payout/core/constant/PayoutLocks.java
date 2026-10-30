package com.paytech.payout.core.constant;

import com.paytech.payout.api.enums.BeneficiaryOwnerType;

/**
 * payout 写入口共用的 Redis 资源锁（technique 第 6 节）
 * <li>同一 Owner 的新建、默认切换、查重与全部单账户命令共用 Owner 锁，保证默认唯一、事件序号连续与查重串行。</li>
 * <li>数据库唯一索引、版本条件更新仍是最终防线。</li>
 */
public final class PayoutLocks {
    /** 锁等待上限（毫秒）：Owner 内命令低并发，提交含两次 Vault 调用，等待 5 秒后返回并发冲突。 */
    public static final int WAIT_MILLIS = 5000;

    /** 租约：-1 由 Watchdog 自动续租。 */
    public static final int LEASE_MILLIS = -1;

    private PayoutLocks() {
    }

    /**
     * Owner 资源锁 Key
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @return PAYOUT_BENEFICIARY_OWNER:{ownerType}:{ownerId}
     */
    public static String beneficiaryOwner(BeneficiaryOwnerType ownerType, Long ownerId) {
        return "PAYOUT_BENEFICIARY_OWNER:" + ownerType.getCode() + ":" + ownerId;
    }
}
