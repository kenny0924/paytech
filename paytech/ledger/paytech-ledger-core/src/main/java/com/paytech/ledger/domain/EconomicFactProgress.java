package com.paytech.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** billing 对一个账簿主体报告的经济事实发布进度（design/04 §6），关账步骤 2 使用。 */
@Getter
@AllArgsConstructor
public class EconomicFactProgress {
    /** 已批准但 EconomicFactRecorded 尚未确认发布的事实数。 */
    private final long pendingCount;

    /** 已确认发布的最大 factId；尚无已发布事实时为空。 */
    private final Long maxPublishedFactId;
}
