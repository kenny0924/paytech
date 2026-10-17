package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 把原始金额归整到币种精度的固定方式。 */
public enum AmountRoundingMode implements CodeEnum {
    /** 四舍五入 */
    HALF_UP;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
