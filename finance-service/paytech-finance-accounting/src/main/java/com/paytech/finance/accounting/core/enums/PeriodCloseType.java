package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关账流程类型。 */
public enum PeriodCloseType implements CodeEnum {
    /** 关账 */
    CLOSE,

    /** 重开 */
    REOPEN;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
