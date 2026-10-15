package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 账簿是否允许接收新的会计分录。 */
public enum BookStatus implements CodeEnum {
    /** 允许过账 */
    OPEN,

    /** 暂停过账 */
    SUSPENDED,

    /** 永久关闭 */
    CLOSED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
