package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 准入收税状态（t_tax_market.collection_status）。 */
public enum MarketCollectionStatus implements CodeEnum {
    /** 不允许收税 */
    BLOCKED,

    /** 允许按本准入收税 */
    ENABLED,

    /** 暂停收税 */
    SUSPENDED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
