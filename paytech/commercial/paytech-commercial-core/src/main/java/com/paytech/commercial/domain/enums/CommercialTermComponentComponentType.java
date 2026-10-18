package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermComponent ComponentType 的数据库闭合值域。 */
public enum CommercialTermComponentComponentType implements CodeEnum {
    PERCENTAGE("PERCENTAGE"),

    FIXED("FIXED"),

    TIERED("TIERED");
    private final String code;
    CommercialTermComponentComponentType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
