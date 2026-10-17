package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 租户授权记录的TenantRoleStatus稳定代码。 */
@lombok.AllArgsConstructor
public enum TenantRoleStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    INACTIVE("INACTIVE");

    private final String code;

    /** 返回持久化的稳定业务代码。 */
    @Override public String getCode() { return code; }
}
