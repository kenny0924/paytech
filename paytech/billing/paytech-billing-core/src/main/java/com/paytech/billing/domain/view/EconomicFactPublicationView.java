package com.paytech.billing.domain.view;

import lombok.Getter;
import lombok.Setter;

/** 账簿主体下已批准经济事实的发布进度（单条聚合查询结果）。 */
@Getter
@Setter
public class EconomicFactPublicationView {
    /** 已批准但未确认发布的事实数。 */
    private long pendingCount;

    /**
     * 未发布事实中最大的 ID
     * <li>没有未发布事实时为空。</li>
     */
    private Long maxPendingFactId;

    /**
     * 已发布事实中最大的 ID
     * <li>尚无已发布事实时为空。</li>
     */
    private Long maxPublishedFactId;
}
