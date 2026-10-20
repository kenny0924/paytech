package com.paytech.ledger.domain;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.core.enums.AdjustmentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账簿内人工调整列表查询条件；过滤字段为空时不限制。 */
@Getter
@AllArgsConstructor
public class ManualAdjustmentQuery {
    /** 账簿主键。 */
    private final long ledgerBookId;

    /** 会计期间主键，可空。 */
    private final Long accountingPeriodId;

    /** 调整状态，可空。 */
    private final AdjustmentStatus status;

    /** 编制人 Account ID，可空。 */
    private final Long makerId;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;
}
