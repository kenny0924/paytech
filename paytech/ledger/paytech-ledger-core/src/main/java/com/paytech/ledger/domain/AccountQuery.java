package com.paytech.ledger.domain;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.api.enums.LedgerOwnerType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账簿内账户列表查询条件；过滤字段为空时不限制。 */
@Getter
@AllArgsConstructor
public class AccountQuery {
    /** 账簿主键。 */
    private final long ledgerBookId;

    /** 科目代码，可空。 */
    private final String accountCode;

    /** 核算对象类型，可空。 */
    private final LedgerOwnerType ownerType;

    /** 核算对象标识，可空。 */
    private final String ownerId;

    /** 余额桶，可空。 */
    private final BalanceBucket balanceBucket;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;
}
