package com.paytech.account.domain.view;

import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.BalanceMovement;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Owner 账户的一条流水及其所属批次与来源（流水、批次、事实、移动四表组合）。 */
@Getter
@AllArgsConstructor
public class AccountEntryView {
    /** 流水。 */
    private final AccountEntry entry;

    /** 所属批次。 */
    private final AccountPosting posting;

    /** 事实批次的处理记录；桶移动批次为空。 */
    private final BalanceAppliedFact appliedFact;

    /** 桶移动批次的移动记录；事实批次为空。 */
    private final BalanceMovement movement;
}
