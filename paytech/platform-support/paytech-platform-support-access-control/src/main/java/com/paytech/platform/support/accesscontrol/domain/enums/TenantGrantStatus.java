package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 租户授权记录的TenantGrantStatus稳定代码。 */
@lombok.AllArgsConstructor
public enum TenantGrantStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    REVOKED("REVOKED"),

    EXPIRED("EXPIRED");

    private final String code;

    /** 返回持久化的稳定业务代码。 */
    @Override public String getCode() { return code; }
}
