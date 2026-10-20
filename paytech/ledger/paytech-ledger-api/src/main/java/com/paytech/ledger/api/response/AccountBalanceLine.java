package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 科目余额行：末级科目或由下级向上汇总的汇总科目（科目余额与试算详情共用）。 */
@Getter
@Setter
public class AccountBalanceLine {
    /** 会计科目主键。 */
    private String chartAccountId;

    /** 科目编号。 */
    private String accountNo;

    /** 科目代码。 */
    private String accountCode;

    /** 科目名称。 */
    private String accountName;

    /** 科目类别：ASSET、LIABILITY、EQUITY、REVENUE、EXPENSE。 */
    private String accountClass;

    /** 科目正常方向：DEBIT 或 CREDIT。 */
    private String normalSide;

    /**
     * 直接上级科目主键
     * <li>一级科目为空。</li>
     */
    private String parentChartAccountId;

    /** 级次 1—4。 */
    private Integer level;

    /** 是否末级可记账；汇总行为 false。 */
    private Boolean postingAllowed;

    /**
     * 期初余额
     * <li>按本行科目正常方向有符号，负数表示反向余额；主货币单位，按账簿币种精度补齐。</li>
     */
    private String openingBalance;

    /** 本期借方发生额；汇总行为直接下级之和。 */
    private String periodDebit;

    /** 本期贷方发生额；汇总行为直接下级之和。 */
    private String periodCredit;

    /**
     * 期末余额
     * <li>期初加本期正常方向净额，按本行科目正常方向有符号。</li>
     */
    private String closingBalance;
}
