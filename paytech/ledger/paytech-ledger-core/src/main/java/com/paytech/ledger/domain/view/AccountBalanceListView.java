package com.paytech.ledger.domain.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账簿某期间的科目余额：末级与各级汇总行，按科目编号排序。 */
@Getter
@AllArgsConstructor
public class AccountBalanceListView {
    /** 账簿主键。 */
    private final Long ledgerBookId;

    /** 会计期间主键。 */
    private final Long periodId;

    /** 账簿币种。 */
    private final String currency;

    /** 有发生额或余额的末级科目及其各级上级，按科目编号升序。 */
    private final List<AccountBalanceView> lines;
}
