package com.paytech.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** billing 对一个账簿主体报告的经济事实发布进度；关账步骤 3 的 billing → account 段使用。 */
@Getter
@AllArgsConstructor
public class EconomicFactProgress {
    /** 已批准但经济事实事件尚未确认发布的事实数。 */
    private final long pendingCount;

    /** 已批准（含已被替代）且已确认发布的事实数。 */
    private final long publishedFactCount;

    /**
     * 已确认发布的最大 factId
     * <li>尚无已发布事实时为空。</li>
     */
    private final Long maxPublishedFactId;
}
