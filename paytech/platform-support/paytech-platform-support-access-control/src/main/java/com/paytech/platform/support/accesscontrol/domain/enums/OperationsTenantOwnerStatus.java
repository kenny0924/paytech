package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsTenantOwnerStatus 的稳定外部代码。 */
public enum OperationsTenantOwnerStatus implements CodeEnum {
    PENDING("PENDING"),

    ACTIVE("ACTIVE"),

    SUSPENDED("SUSPENDED"),

    TERMINATED("TERMINATED");

    private final String code;

    OperationsTenantOwnerStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
