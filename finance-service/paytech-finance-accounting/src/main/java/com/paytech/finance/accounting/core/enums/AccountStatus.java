package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 账户是否允许新的会计分录过账；终态账户仍可查询历史余额。 */
public enum AccountStatus implements CodeEnum {
    /** 允许过账 */
    ACTIVE,

    /** 暂停过账 */
    INACTIVE,

    /** 永久关闭 */
    CLOSED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
