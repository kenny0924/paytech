package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 财务主体能否用于新账簿；关闭后不得恢复。 */
public enum FinanceEntityStatus implements CodeEnum {
    /** 有效 */
    ACTIVE,

    /** 已关闭 */
    CLOSED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
