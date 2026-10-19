package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** PricingProfile Status 的数据库闭合值域。 */
public enum PricingProfileStatus implements CodeEnum {
    DRAFT("DRAFT"),

    ACTIVE("ACTIVE"),

    RETIRED("RETIRED");
    private final String code;
    PricingProfileStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
