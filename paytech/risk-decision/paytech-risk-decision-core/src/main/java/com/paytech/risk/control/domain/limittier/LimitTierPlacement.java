package com.paytech.risk.control.domain.limittier;

import java.time.Instant;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierSource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批准后档位行的新值
 * <li>进入或延长 OBSERVATION 时观察字段非空；进入 RESTRICTED 时 previousTier 为原档位；其余档位两者为空。</li>
 */
@Getter
@AllArgsConstructor
public final class LimitTierPlacement {
    /** 新档位。 */
    private final LimitTier tier;

    /** 进入 RESTRICTED 前的档位；非 RESTRICTED 为空。 */
    private final LimitTier previousTier;

    /** 来源。 */
    private final LimitTierSource sourceType;

    /** 产生新档位的变更记录 ID。 */
    private final long sourceChangeId;

    /** 生效时间，同时作为档位行更新时间。 */
    private final Instant effectiveTime;

    /** 观察期开始时间。 */
    private final Instant observationStartedTime;

    /** 观察期目标天数。 */
    private final Integer observationTargetDays;

    /** 观察期目标放行笔数。 */
    private final Integer observationTargetTransactions;

    /** 开始或延长观察期的变更记录 ID。 */
    private final Long observationStartedByChangeId;
}
