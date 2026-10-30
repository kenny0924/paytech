package com.paytech.risk.control.domain.limittier;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批准档位变更申请的已验证上下文
 * <li>申请置为 APPROVED、档位行改为新值、旧 TIER 名单成员置为 SUPERSEDED、新名单成员插入，同一事务完成。</li>
 * <li>延长观察（档位不变）时两个名单成员均为空；旧成员缺失（数据被破坏）时 closingEntry 为空，批准同时修复投影。</li>
 */
@Getter
@AllArgsConstructor
public class LimitTierApprovalContext {
    /** 待批准的申请。 */
    private final ClientLimitTierChange change;

    /** 当前档位行。 */
    private final ClientLimitTier tier;

    /** 档位行新值。 */
    private final LimitTierPlacement placement;

    /** 需结束的旧档位名单成员；可空。 */
    private final RiskListEntry closingEntry;

    /** 新档位名单成员；档位不变时为空。 */
    private final RiskListEntry newEntry;

    /** 审批人账号 ID。 */
    private final long actorAccountId;

    /** 审批意见；可空。 */
    private final String comment;

    /** 本次处理时间。 */
    private final Instant now;
}
