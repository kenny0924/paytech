package com.paytech.finance.accounting.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_trial_balance_line：不可变地冻结运行账簿内每个会计科目汇总后的期初余额、本期借贷发生额和期末余额。 */
@Getter
@Setter
public class TrialBalanceLine {
    /**
     * 试算平衡明细行的 Snowflake 主键
     */
    private Long id;

    /**
     * 该明细所属的本库试算平衡运行
     */
    private Long runId;

    /**
     * 被试算且由运行账簿范围唯一限定的本库会计科目定义
     */
    private Long chartAccountId;

    /**
     * 按科目正常方向表达的期间期初余额
     */
    private BigDecimal openingBalance;

    /**
     * 期间内该科目的借方累计发生金额
     */
    private BigDecimal periodDebit;

    /**
     * 期间内该科目的贷方累计发生金额
     */
    private BigDecimal periodCredit;

    /**
     * 按科目正常方向表达的期间期末余额
     */
    private BigDecimal closingBalance;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
