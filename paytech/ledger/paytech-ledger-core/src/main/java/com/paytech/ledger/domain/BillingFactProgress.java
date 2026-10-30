package com.paytech.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** billing 对一个账簿主体报告的计费事实发布进度；关账步骤 3 的 billing → account 段使用。 */
@Getter
@AllArgsConstructor
public class BillingFactProgress {
    /** 已批准但计费事实事件尚未确认发布的事实数。 */
    private final long pendingCount;

    /**
     * 未发布事实中最大的 factId
     * <li>没有未发布事实时为空；只用于运维展示，不参与判定。</li>
     */
    private final Long maxPendingFactId;

    /** 已批准（含已被替代）且已确认发布的事实数。 */
    private final long publishedFactCount;

    /**
     * 已确认发布的最大 factId
     * <li>尚无已发布事实时为空。</li>
     */
    private final Long maxPublishedFactId;
}
