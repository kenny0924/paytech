package com.paytech.billing.domain;

import java.time.Instant;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;

import lombok.Getter;
import lombok.Setter;

/**
 * 计费事实管理列表的筛选条件
 * <li>entityNo 与 currency 必填（命中 idx_fact_book_oe），其余为空表示不筛选；登记时间区间左闭右开。</li>
 */
@Getter
@Setter
public class BillingFactSearchCriteria {
    /** Operating Entity 编号 */
    private String entityNo;

    /** 币种 */
    private String currency;

    /** Client；为空不筛选 */
    private Long clientId;

    /** 登记状态；为空不筛选 */
    private BillingFactStatus status;

    /** 事实类型；为空不筛选 */
    private BillingFactType factType;

    /** 登记时间下限（含）；为空不筛选 */
    private Instant createdFrom;

    /** 登记时间上限（不含）；为空不筛选 */
    private Instant createdTo;
}
