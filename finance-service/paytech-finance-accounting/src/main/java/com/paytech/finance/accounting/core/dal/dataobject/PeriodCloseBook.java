package com.paytech.finance.accounting.core.dal.dataobject;

import java.time.Instant;

import com.paytech.finance.accounting.core.enums.CloseBookStatus;

import lombok.Getter;
import lombok.Setter;

/** t_period_close_book：逐账簿冻结关账或重开的 Journal Sequence 水位、最终试算和平衡结转指令；FINALIZED 必须在同一事务重验同一账簿、期间和水位的成功 Final Trial、无开放异常、对应 Journal、同水位期间余额及七个关账步骤全部成功。 */
@Getter
@Setter
public class PeriodCloseBook {
    /**
     * 关账账簿范围记录的 Snowflake 主键
     */
    private Long id;

    /**
     * 该账簿范围所属的本库关账或重开流程
     */
    private Long periodCloseId;

    /**
     * 本流程必须独立关账或重开的本库经营主体币种账簿
     */
    private Long ledgerBookId;

    /**
     * 本流程针对该账簿锁定的最大 Journal Sequence
     */
    private Long asOfSequence;

    /**
     * 该账簿在最终复核步骤采用且必须成功平衡的试算运行
     * <li>可为空。</li>
     */
    private Long finalTrialRunId;

    /**
     * 该账簿由本流程生成的 CLOSING 或 REOPENING 会计指令
     * <li>可为空。</li>
     */
    private Long postingInstructionId;

    /**
     * 该账簿水位是否已经完成最终试算和结转证据冻结
     * <li>允许值：LOCKED、FINALIZED。</li>
     */
    private CloseBookStatus status;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
