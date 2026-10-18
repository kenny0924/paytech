package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 租户授权记录的RolePermissionStatus稳定代码。 */
@lombok.AllArgsConstructor
public enum RolePermissionStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    REVOKED("REVOKED");

    private final String code;

    /** 返回持久化的稳定业务代码。 */
    @Override public String getCode() { return code; }
}
