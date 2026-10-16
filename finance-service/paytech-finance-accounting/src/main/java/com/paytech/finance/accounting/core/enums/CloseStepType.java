package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关账固定步骤，按声明顺序执行。 */
public enum CloseStepType implements CodeEnum {
    /** 锁定水位 */
    LOCK_SEQUENCE,

    /** 异步任务与分录完整性检查 */
    CHECK_ASYNC_AND_JOURNAL,

    /** 账户余额核对 */
    RECONCILE_ACCOUNT_BALANCES,

    /** 生成试算 */
    GENERATE_TRIAL_BALANCE,

    /** 试算异常检查 */
    RESOLVE_EXCEPTIONS,

    /** 损益结转 */
    POST_PROFIT_CLOSE,

    /** 最终试算 */
    FINAL_TRIAL_BALANCE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
