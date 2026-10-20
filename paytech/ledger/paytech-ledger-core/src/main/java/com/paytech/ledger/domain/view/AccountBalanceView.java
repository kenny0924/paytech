package com.paytech.ledger.domain.view;

import java.math.BigDecimal;

import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** 科目与其期初、本期借贷、期末金额的组合结果：末级行来自分录聚合或试算行，汇总行由科目树向上累加。 */
@Getter
@Setter
public class AccountBalanceView {
    /** 会计科目主键。 */
    private Long chartAccountId;

    /** 科目编号。 */
    private String accountNo;

    /** 科目代码。 */
    private String accountCode;

    /** 科目名称。 */
    private String accountName;

    /** 科目类别。 */
    private AccountClass accountClass;

    /** 科目正常方向；期初与期末按该方向有符号表达。 */
    private NormalSide normalSide;

    /** 直接上级科目主键；一级科目为空。 */
    private Long parentChartAccountId;

    /** 级次 1—4，由科目编号长度推出。 */
    private Integer level;

    /** 是否末级可记账；汇总行为否。 */
    private Boolean postingAllowed;

    /** 期初余额（正常方向有符号）。 */
    private BigDecimal openingBalance;

    /** 本期借方发生额。 */
    private BigDecimal periodDebit;

    /** 本期贷方发生额。 */
    private BigDecimal periodCredit;

    /** 期末余额（正常方向有符号）。 */
    private BigDecimal closingBalance;
}
