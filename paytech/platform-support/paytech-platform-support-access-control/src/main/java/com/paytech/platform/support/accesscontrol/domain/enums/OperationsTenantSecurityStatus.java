package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsTenantSecurityStatus 的稳定外部代码。 */
public enum OperationsTenantSecurityStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    SUSPENDED("SUSPENDED");

    private final String code;

    OperationsTenantSecurityStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
