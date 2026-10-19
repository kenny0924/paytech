package com.paytech.ledger.domain;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.core.enums.FinanceEntityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 财务主体列表查询条件；过滤字段为空时不限制。 */
@Getter
@AllArgsConstructor
public class FinanceEntityQuery {
    /** 主体状态，可空。 */
    private final FinanceEntityStatus status;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;
}
