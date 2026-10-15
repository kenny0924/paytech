package com.paytech.finance.accounting.domain.view;

import java.math.BigDecimal;

import com.paytech.finance.accounting.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** 试算聚合 SQL 按可过账科目汇总的期初与本期发生额。 */
@Getter
@Setter
public class TrialBalanceAggregateView {
    /** 会计科目主键。 */
    private Long chartAccountId;

    /** 科目正常余额方向。 */
    private NormalSide normalSide;

    /** 过账日早于期间开始的有符号累计（正常方向为正）。 */
    private BigDecimal openingBalance;

    /** 期间内借方发生额。 */
    private BigDecimal periodDebit;

    /** 期间内贷方发生额。 */
    private BigDecimal periodCredit;
}
