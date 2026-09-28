package com.paytech.finance.accounting.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Owner 应付余额的成熟和锁定桶；独立科目使用 NONE。 */
public enum BalanceBucket implements CodeEnum {
    PENDING,

    AVAILABLE,

    LOCKED,

    NONE;

    @Override
    public String getCode() {
        return name();
    }
}
