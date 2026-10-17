package com.paytech.ledger.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_ledger_period_balance：保存 Trial Balance 与关账流程按账簿和科目汇总冻结的期初、本期发生额和期末余额。 */
@Getter
@Setter
public class LedgerPeriodBalance {
    /**
     * 账簿期间科目余额的服务侧雪花主键
     */
    private Long id;

    /**
     * 本余额行所属的 Finance 会计期间主键
     */
    private Long periodId;

    /**
     * 本余额行严格归属的会计主体币种账簿
     */
    private Long ledgerBookId;

    /**
     * 本余额行汇总的本库可过账会计科目定义
     */
    private Long chartAccountId;

    /**
     * 会计期间开始时按科目正常方向计算的余额
     */
    private BigDecimal openingBalance;

    /**
     * 会计期间内累计借方发生金额
     */
    private BigDecimal periodDebit;

    /**
     * 会计期间内累计贷方发生金额
     */
    private BigDecimal periodCredit;

    /**
     * 截止试算序号按科目正常方向计算的期末余额
     */
    private BigDecimal closingBalance;

    /**
     * 本余额行已投影到的 Journal Sequence 水位；只允许较新水位覆盖，相同 Run 和水位幂等，旧水位不得覆盖较新投影
     */
    private Long asOfSequence;

    /**
     * 产生或最终冻结本余额行的试算平衡任务主键
     */
    private Long trialBalanceRunId;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
