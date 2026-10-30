package com.paytech.risk.control.domain.limittier;

import java.time.Duration;
import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.enums.LimitTier;

/**
 * 观察期进度：天数 = 当前时间 − 开始时间（整天），放行笔数 = 开始后风控放行的支付尝试数（近似）
 *
 * @param startedTime 开始时间
 * @param daysElapsed 已过整天数
 * @param targetDays 目标天数
 * @param allowedTransactions 放行笔数
 * @param targetTransactions 目标放行笔数
 * @param due 天数或笔数任一达到目标（只提示，不自动升档）
 */
public record TierObservation(Instant startedTime, int daysElapsed, int targetDays, long allowedTransactions,
    int targetTransactions, boolean due) {

    /** 无承保时的默认观察期天数。 */
    public static final int DEFAULT_TARGET_DAYS = 30;

    /** 无承保时的默认观察期笔数。 */
    public static final int DEFAULT_TARGET_TRANSACTIONS = 100;

    /**
     * 计算 OBSERVATION 档位行的观察期进度
     *
     * @param tier 档位行
     * @param allowedTransactions 观察期内放行笔数
     * @param now 当前时间
     * @return 进度；非 OBSERVATION 或缺少开始时间为空
     */
    public static TierObservation of(ClientLimitTier tier, long allowedTransactions, Instant now) {
        if (!inObservation(tier)) {
            return null;
        }
        int days = elapsedDays(tier.getObservationStartedTime(), now);
        int targetDays = tier.getObservationTargetDays();
        int targetTransactions = tier.getObservationTargetTransactions();
        return new TierObservation(tier.getObservationStartedTime(), days, targetDays, allowedTransactions,
            targetTransactions, days >= targetDays || allowedTransactions >= targetTransactions);
    }

    /** 档位行处于有开始时间的观察期。 */
    public static boolean inObservation(ClientLimitTier tier) {
        return tier != null && tier.getTier() == LimitTier.OBSERVATION && tier.getObservationStartedTime() != null;
    }

    /** 自开始时间起已过的整天数，开始时间晚于当前时间时为 0。 */
    public static int elapsedDays(Instant startedTime, Instant now) {
        return (int)Math.max(0L, Duration.between(startedTime, now).toDays());
    }
}
