package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsContextType 的稳定外部代码。 */
public enum OperationsContextType implements CodeEnum {
    BOOTSTRAP("BOOTSTRAP"),

    TENANT("TENANT");

    private final String code;

    OperationsContextType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
