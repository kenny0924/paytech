package com.paytech.ledger.domain;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.core.enums.LedgerEntityType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 财务日历列表查询条件；过滤字段为空时不限制。 */
@Getter
@AllArgsConstructor
public class FiscalCalendarQuery {
    /** 主体类型，可空。 */
    private final LedgerEntityType entityType;

    /** 主体标识，可空。 */
    private final String entityId;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;
}
