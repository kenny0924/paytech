package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** BusinessChangeRecord RecordType 的数据库闭合值域。 */
public enum BusinessChangeRecordRecordType implements CodeEnum {
    COMMERCIAL_TERM_VERSION("COMMERCIAL_TERM_VERSION"),

    AGREEMENT("AGREEMENT"),

    COMMERCIAL_TERM_PLAN("COMMERCIAL_TERM_PLAN"),

    PAYMENT_METHOD_PROFILE("PAYMENT_METHOD_PROFILE");

    private final String code;
    BusinessChangeRecordRecordType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
