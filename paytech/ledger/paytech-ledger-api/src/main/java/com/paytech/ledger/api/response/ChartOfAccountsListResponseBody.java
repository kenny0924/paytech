package com.paytech.ledger.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 全部科目表。 */
@Getter
@Setter
public class ChartOfAccountsListResponseBody {
    /** 按科目表代码升序的科目表。 */
    private List<ChartOfAccountsResponseBody> items;
}
