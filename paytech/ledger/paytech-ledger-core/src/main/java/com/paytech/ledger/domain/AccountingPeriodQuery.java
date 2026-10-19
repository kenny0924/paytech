package com.paytech.ledger.domain;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 日历下会计期间列表查询条件。 */
@Getter
@AllArgsConstructor
public class AccountingPeriodQuery {
    /** 财务日历主键。 */
    private final long fiscalCalendarId;

    /** 财年，可空时不限制。 */
    private final Integer fiscalYear;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;
}
