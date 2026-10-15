package com.paytech.finance.accounting.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.finance.accounting.core.enums.TrialBalanceStatus;

import lombok.Getter;
import lombok.Setter;

/** t_trial_balance_run：按账簿、会计期间和 Journal Sequence 水位执行可重放试算平衡，并冻结可过账叶子科目期末余额按实际借贷方向拆分的合计。 */
@Getter
@Setter
public class TrialBalanceRun {
    /**
     * 试算平衡运行的 Snowflake 主键
     */
    private Long id;

    /**
     * 执行试算平衡的本库经营主体币种账簿
     */
    private Long ledgerBookId;

    /**
     * 执行试算平衡的本库会计期间
     */
    private Long periodId;

    /**
     * 同一账簿和期间内从一开始连续递增的试算序号
     */
    private Integer runNo;

    /**
     * 本次试算纳入的最大 Journal Sequence
     */
    private Long asOfSequence;

    /**
     * 试算平衡运行当前执行结果
     * <li>允许值：CREATED、RUNNING、SUCCEEDED、FAILED。</li>
     */
    private TrialBalanceStatus status;

    /**
     * 可过账叶子科目的期末余额按科目正常方向和有符号余额拆分后处于实际借方的金额合计，不表示本期借方发生额
     */
    private BigDecimal totalDebit;

    /**
     * 可过账叶子科目的期末余额按科目正常方向和有符号余额拆分后处于实际贷方的金额合计，不表示本期贷方发生额
     */
    private BigDecimal totalCredit;

    /**
     * 运行终态冻结 Scope、水位、试算行和异常发现事实的 Canonical SHA-256 哈希，不包含随后可变的异常解决字段；运行态必须为空
     * <li>可为空。</li>
     */
    private String contentHash;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
