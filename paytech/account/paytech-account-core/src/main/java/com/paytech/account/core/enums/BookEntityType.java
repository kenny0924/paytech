package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** ledger 账簿主体类型，与 ledger 账簿身份及 billing 账簿主体参数一致。 */
public enum BookEntityType implements CodeEnum {
    /** Operating Entity，标识为 entityNo */
    OPERATING_ENTITY,

    /** Finance Entity，标识为 ledger 本地主键十进制字符串 */
    FINANCE_ENTITY;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
