package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsMemberStatus 的稳定外部代码。 */
public enum OperationsMemberStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    SUSPENDED("SUSPENDED"),

    REVOKED("REVOKED");

    private final String code;

    OperationsMemberStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
