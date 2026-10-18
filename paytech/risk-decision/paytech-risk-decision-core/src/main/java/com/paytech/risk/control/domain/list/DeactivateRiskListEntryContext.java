package com.paytech.risk.control.domain.list;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工失效名单成员：已校验归属与状态的成员、操作人、原因与本次时间。 */
@Getter
@AllArgsConstructor
public class DeactivateRiskListEntryContext {
    /** 当前生效成员。 */
    private final RiskListEntry entry;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 失效原因。 */
    private final String reason;

    /** 本次 UTC 时间，同时作为生效终点上限。 */
    private final Instant now;

    /** 生效终点：不晚于原终点。 */
    public Instant effectiveTo() {
        return entry.getEffectiveTo() != null && entry.getEffectiveTo().isBefore(now) ? entry.getEffectiveTo() : now;
    }
}
