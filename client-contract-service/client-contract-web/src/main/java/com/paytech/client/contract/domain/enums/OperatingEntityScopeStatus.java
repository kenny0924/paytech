package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** OperatingEntityScope Status 的数据库闭合值域。 */
public enum OperatingEntityScopeStatus implements CodeEnum {
    DRAFT("DRAFT"),

    ACTIVE("ACTIVE"),

    ENDED("ENDED");
    private final String code;
    OperatingEntityScopeStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
