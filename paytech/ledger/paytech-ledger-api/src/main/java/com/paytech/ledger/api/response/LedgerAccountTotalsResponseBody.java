package com.paytech.ledger.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 账簿内全部 Owner 账户按分录聚合的余额。 */
@Getter
@Setter
public class LedgerAccountTotalsResponseBody {
    /** 账簿主键。 */
    private String ledgerBookId;

    /** 主体类型。 */
    private String entityType;

    /** 主体标识。 */
    private String entityId;

    /** 账簿币种。 */
    private String currency;

    /** 按科目代码、核算对象、余额桶排序的账户。 */
    private List<LedgerAccountTotalResponseBody> items;
}
