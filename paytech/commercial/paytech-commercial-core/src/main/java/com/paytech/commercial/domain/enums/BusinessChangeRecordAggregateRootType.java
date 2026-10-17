package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** BusinessChangeRecord AggregateRootType 的数据库闭合值域。 */
public enum BusinessChangeRecordAggregateRootType implements CodeEnum {
    AGREEMENT("AGREEMENT"),

    COMMERCIAL_TERM_PLAN("COMMERCIAL_TERM_PLAN"),

    PAYMENT_METHOD_PROFILE("PAYMENT_METHOD_PROFILE");

    private final String code;
    BusinessChangeRecordAggregateRootType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
