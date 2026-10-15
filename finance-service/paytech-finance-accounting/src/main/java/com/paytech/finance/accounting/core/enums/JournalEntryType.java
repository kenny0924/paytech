package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 会计分录头的受控类型。 */
public enum JournalEntryType implements CodeEnum {
    /** 标准分录 */
    STANDARD,

    /** 整条冲销 */
    REVERSAL,

    /** 调整分录 */
    ADJUSTMENT,

    /** 关账结转 */
    CLOSING,

    /** 重开结转 */
    REOPENING;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
