package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Agreement Status 的数据库闭合值域。 */
public enum AgreementStatus implements CodeEnum {
    DRAFT("DRAFT"),

    PENDING_SIGNATURE("PENDING_SIGNATURE"),

    SIGNED("SIGNED"),

    ACTIVE("ACTIVE"),

    EXPIRED("EXPIRED"),

    TERMINATED("TERMINATED"),

    CANCELLED("CANCELLED");
    private final String code;
    AgreementStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
