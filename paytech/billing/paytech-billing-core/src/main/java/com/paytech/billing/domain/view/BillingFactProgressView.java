package com.paytech.billing.domain.view;

import lombok.Getter;
import lombok.Setter;

/** 账簿主体下已批准计费事实的发布进度（单条聚合查询结果，未发布与已发布两段计数）。 */
@Getter
@Setter
public class BillingFactProgressView {
    /** 已批准（含已被替代）但未确认发布的事实数。 */
    private long pendingCount;

    /**
     * 未发布事实中最大的 ID
     * <li>没有未发布事实时为空。</li>
     */
    private Long maxPendingFactId;

    /** 已批准（含已被替代）且已确认发布的事实数。 */
    private long publishedFactCount;

    /**
     * 已发布事实中最大的 ID
     * <li>尚无已发布事实时为空。</li>
     */
    private Long maxPublishedFactId;
}
