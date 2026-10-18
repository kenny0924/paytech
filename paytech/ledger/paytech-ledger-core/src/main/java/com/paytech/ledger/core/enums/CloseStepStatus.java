package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关账步骤状态；终态必须有结果摘要与完成时间。 */
public enum CloseStepStatus implements CodeEnum {
    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 成功 */
    SUCCEEDED,

    /** 失败 */
    FAILED,

    /** 跳过 */
    SKIPPED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
