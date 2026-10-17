package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsRefreshStatus 的稳定外部代码。 */
public enum OperationsRefreshStatus implements CodeEnum {
    ISSUED("ISSUED"),

    CONSUMED("CONSUMED"),

    REVOKED("REVOKED"),

    EXPIRED("EXPIRED");

    private final String code;

    OperationsRefreshStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
