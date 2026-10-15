package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Owner 应付余额的成熟和锁定桶；独立科目使用 NONE。 */
public enum BalanceBucket implements CodeEnum {
    /** 待成熟 */
    PENDING,

    /** 已成熟可用 */
    AVAILABLE,

    /** 已锁定 */
    LOCKED,

    /** 不分桶 */
    NONE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
