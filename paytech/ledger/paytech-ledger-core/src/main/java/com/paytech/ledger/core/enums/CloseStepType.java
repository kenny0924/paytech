package com.paytech.ledger.core.enums;

import java.util.List;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关账与重开流程的固定步骤；各流程按 {@link #closeSteps()} 或 {@link #reopenSteps()} 的顺序建立步骤。 */
public enum CloseStepType implements CodeEnum {
    /** 锁定水位 */
    LOCK_SEQUENCE,

    /** 异步任务与分录完整性检查 */
    CHECK_ASYNC_AND_JOURNAL,

    /** 入账进度检查：billing → account 已批准事实全部入账，account → ledger 各过账流序号已追平 */
    CHECK_ACCOUNT_POSTING_PROGRESS,

    /** 生成试算 */
    GENERATE_TRIAL_BALANCE,

    /** 试算异常检查 */
    RESOLVE_EXCEPTIONS,

    /** 损益结转 */
    POST_PROFIT_CLOSE,

    /** 最终试算 */
    FINAL_TRIAL_BALANCE,

    /** 规划重开冲回 */
    PLAN_REOPEN_REVERSAL,

    /** 过账重开冲回 */
    POST_REOPEN_REVERSAL;

    private static final List<CloseStepType> CLOSE_STEPS = List.of(LOCK_SEQUENCE, CHECK_ASYNC_AND_JOURNAL,
        CHECK_ACCOUNT_POSTING_PROGRESS, GENERATE_TRIAL_BALANCE, RESOLVE_EXCEPTIONS, POST_PROFIT_CLOSE, FINAL_TRIAL_BALANCE);

    private static final List<CloseStepType> REOPEN_STEPS = List.of(LOCK_SEQUENCE, PLAN_REOPEN_REVERSAL,
        POST_REOPEN_REVERSAL);

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }

    /** 关账流程的 7 个步骤，按执行顺序。 */
    public static List<CloseStepType> closeSteps() {
        return CLOSE_STEPS;
    }

    /** 重开流程的 3 个步骤，按执行顺序。 */
    public static List<CloseStepType> reopenSteps() {
        return REOPEN_STEPS;
    }
}
