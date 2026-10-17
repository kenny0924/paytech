package com.paytech.ledger.domain.view;

import java.math.BigDecimal;

import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** 试算行与其会计科目的组合读取结果。 */
@Getter
@Setter
public class TrialBalanceLineView {
    /** 会计科目主键。 */
    private Long chartAccountId;

    /** 科目代码。 */
    private String accountCode;

    /** 科目名称。 */
    private String accountName;

    /** 科目类别。 */
    private AccountClass accountClass;

    /** 科目正常方向。 */
    private NormalSide normalSide;

    /** 期初余额（正常方向有符号）。 */
    private BigDecimal openingBalance;

    /** 本期借方发生额。 */
    private BigDecimal periodDebit;

    /** 本期贷方发生额。 */
    private BigDecimal periodCredit;

    /** 期末余额（正常方向有符号）。 */
    private BigDecimal closingBalance;
}
