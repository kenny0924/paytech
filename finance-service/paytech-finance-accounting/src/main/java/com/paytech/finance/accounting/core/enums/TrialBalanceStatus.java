package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 试算平衡运行状态；同步试算直接写入终态。 */
public enum TrialBalanceStatus implements CodeEnum {
    /** 已创建 */
    CREATED,

    /** 运行中 */
    RUNNING,

    /** 成功，借贷相等且无异常 */
    SUCCEEDED,

    /** 失败，存在异常或借贷不等 */
    FAILED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
