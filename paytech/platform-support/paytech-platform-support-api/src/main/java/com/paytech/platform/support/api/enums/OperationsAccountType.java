package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsAccountType 的稳定外部代码。 */
public enum OperationsAccountType implements CodeEnum {
    HUMAN("HUMAN"),

    BOOTSTRAP_ADMIN("BOOTSTRAP_ADMIN");

    private final String code;

    OperationsAccountType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
