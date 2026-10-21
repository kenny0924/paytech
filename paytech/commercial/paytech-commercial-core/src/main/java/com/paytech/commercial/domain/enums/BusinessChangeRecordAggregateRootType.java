package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** BusinessChangeRecord AggregateRootType 的数据库闭合值域。 */
public enum BusinessChangeRecordAggregateRootType implements CodeEnum {
    AGREEMENT("AGREEMENT"),

    COMMERCIAL_TERM_PLAN("COMMERCIAL_TERM_PLAN"),

    PAYMENT_METHOD_PROFILE("PAYMENT_METHOD_PROFILE"),

    /** 运营维护的产品目录（可开通项、可配维度与计费事件挂接），单一 Root。 */
    PRODUCT_CATALOG("PRODUCT_CATALOG");

    private final String code;
    BusinessChangeRecordAggregateRootType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
