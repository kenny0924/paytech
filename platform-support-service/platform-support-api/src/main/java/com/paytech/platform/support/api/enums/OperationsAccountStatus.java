package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsAccountStatus 的稳定外部代码。 */
public enum OperationsAccountStatus implements CodeEnum {
    PENDING_VERIFICATION("PENDING_VERIFICATION"),

    ACTIVE("ACTIVE"),

    DISABLED("DISABLED"),

    CLOSED("CLOSED");

    private final String code;

    OperationsAccountStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
