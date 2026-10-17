package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsConsoleResourceType 的稳定外部代码。 */
public enum OperationsConsoleResourceType implements CodeEnum {
    MENU("MENU"),

    BUTTON("BUTTON");

    private final String code;

    OperationsConsoleResourceType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
