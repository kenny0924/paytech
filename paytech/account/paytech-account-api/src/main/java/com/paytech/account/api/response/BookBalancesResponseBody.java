package com.paytech.account.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一个 ledger 账簿的全部余额账户。 */
@Getter
@Setter
public class BookBalancesResponseBody {
    /** ledger 账簿主键。 */
    private String ledgerBookId;

    /** 账簿主体类型。 */
    private String bookEntityType;

    /** 账簿主体标识。 */
    private String bookEntityId;

    /** 账簿币种。 */
    private String currency;

    /** 按科目代码、核算对象、余额桶排序的余额账户。 */
    private List<BookBalanceResponseBody> items;
}
