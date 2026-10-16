package com.paytech.finance.accounting.core.dal.dataobject;

import java.time.Instant;

import com.paytech.finance.accounting.core.enums.CloseStepStatus;
import com.paytech.finance.accounting.core.enums.CloseStepType;

import lombok.Getter;
import lombok.Setter;

/** t_close_step：按固定顺序记录数据完整性、对账、试算、损益结转和最终复核步骤及结果。 */
@Getter
@Setter
public class CloseStep {
    /**
     * 关账执行步骤的 Snowflake 主键
     */
    private Long id;

    /**
     * 该步骤所属的本库关账或重开流程
     */
    private Long periodCloseId;

    /**
     * 同一流程内按固定定义连续递增的步骤序号
     */
    private Integer stepNo;

    /**
     * 关账或重开流程中的固定执行步骤
     * <li>允许值：LOCK_SEQUENCE、CHECK_ASYNC_AND_JOURNAL、RECONCILE_ACCOUNT_BALANCES、GENERATE_TRIAL_BALANCE、RESOLVE_EXCEPTIONS、POST_PROFIT_CLOSE、FINAL_TRIAL_BALANCE、PLAN_REOPEN_REVERSAL、POST_REOPEN_REVERSAL。</li>
     */
    private CloseStepType stepType;

    /**
     * 关账步骤当前执行状态
     * <li>允许值：PENDING、RUNNING、SUCCEEDED、FAILED、SKIPPED。</li>
     */
    private CloseStepStatus status;

    /**
     * 本步骤输入和确定性结果的 Canonical SHA-256 哈希
     * <li>可为空。</li>
     */
    private String resultHash;

    /**
     * 步骤进入成功、失败或跳过终态的 UTC 微秒时间
     * <li>可为空。</li>
     */
    private Instant completedTime;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
