package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsCredentialStatus 的稳定外部代码。 */
public enum OperationsCredentialStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    REVOKED("REVOKED");

    private final String code;

    OperationsCredentialStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
