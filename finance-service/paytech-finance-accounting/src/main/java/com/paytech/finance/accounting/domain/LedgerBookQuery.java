package com.paytech.finance.accounting.domain;

import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账簿列表查询条件；过滤字段为空时不限制。 */
@Getter
@AllArgsConstructor
public class LedgerBookQuery {
    /** 主体类型，可空。 */
    private final LedgerEntityType entityType;

    /** 主体标识，可空。 */
    private final String entityId;

    /** 币种，可空。 */
    private final String currency;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;
}
