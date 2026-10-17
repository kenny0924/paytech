package com.paytech.platform.support.auditsecurity.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Operations OperationsAuditChainStatus 的稳定外部代码。 */
public enum OperationsAuditChainStatus implements CodeEnum {
    OPEN("OPEN"),

    SEALED("SEALED"),

    ARCHIVED("ARCHIVED");

    private final String code;

    OperationsAuditChainStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
