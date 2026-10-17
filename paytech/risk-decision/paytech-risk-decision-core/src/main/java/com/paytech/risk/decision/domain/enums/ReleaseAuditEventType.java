package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum ReleaseAuditEventType implements CodeEnum {
    STATIC_CHECK("STATIC_CHECK"),

    APPROVAL("APPROVAL"),

    STATE_CHANGE("STATE_CHANGE");

    private final String code;

    ReleaseAuditEventType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
