package com.paytech.risk.control.domain.limittier;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 为尚无档位的 Client 建档（承保首次批准或存量迁移）的已验证上下文
 * <li>三行同一事务写入；Client 已有档位（并发建档）时整体跳过。</li>
 */
@Getter
@AllArgsConstructor
public class LimitTierEstablishContext {
    /** 新档位行。 */
    private final ClientLimitTier tier;

    /** 直接 APPROVED 的建档变更记录。 */
    private final ClientLimitTierChange change;

    /** 对应 TIER 名单的新成员。 */
    private final RiskListEntry entry;
}
