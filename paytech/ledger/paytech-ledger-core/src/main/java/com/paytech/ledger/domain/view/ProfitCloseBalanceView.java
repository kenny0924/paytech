package com.paytech.ledger.domain.view;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 损益结转：一个收入/费用账户在本期水位内的借贷发生额。 */
@Getter
@Setter
public class ProfitCloseBalanceView {
    /** 账户主键。 */
    private Long accountId;

    /** 账户所属科目主键。 */
    private Long chartAccountId;

    /** 本期借方发生额。 */
    private BigDecimal periodDebit;

    /** 本期贷方发生额。 */
    private BigDecimal periodCredit;
}
