package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 试算异常处置状态。 */
public enum TrialExceptionStatus implements CodeEnum {
    /** 待处置 */
    OPEN,

    /** 审核中 */
    UNDER_REVIEW,

    /** 已解决 */
    RESOLVED,

    /** 已豁免 */
    WAIVED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
