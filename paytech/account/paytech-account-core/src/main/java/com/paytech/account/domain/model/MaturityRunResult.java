package com.paytech.account.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮到期转可用的结果。 */
@Getter
@AllArgsConstructor
public class MaturityRunResult {
    /** 本轮标记 MATURED 的事实数。 */
    private final int maturedFactCount;

    /** 本轮新建的 FACT_MATURITY 移动数。 */
    private final int movementCount;

    /** 本轮处理失败、保留 PENDING 等下轮重试的事实数。 */
    private final int failedFactCount;
}
