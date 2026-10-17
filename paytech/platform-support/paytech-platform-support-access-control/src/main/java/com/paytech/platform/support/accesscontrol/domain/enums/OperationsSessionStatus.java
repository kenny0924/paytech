package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsSessionStatus 的稳定外部代码。 */
public enum OperationsSessionStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    REVOKED("REVOKED"),

    EXPIRED("EXPIRED"),

    COMPROMISED("COMPROMISED");

    private final String code;

    OperationsSessionStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
