package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 会计期间是否允许新分录过账。 */
public enum PeriodStatus implements CodeEnum {
    /** 未开放 */
    FUTURE,

    /** 开放过账 */
    OPEN,

    /** 关账中 */
    CLOSING,

    /** 已关闭 */
    CLOSED,

    /** 重开中 */
    REOPENING;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
