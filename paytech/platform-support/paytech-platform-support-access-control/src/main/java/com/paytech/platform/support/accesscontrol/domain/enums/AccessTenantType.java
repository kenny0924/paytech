package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Platform Access Tenant 的封闭类型。 */
@Getter
@AllArgsConstructor
public enum AccessTenantType implements CodeEnum {
    CLIENT("CLIENT"),

    PARTNER("PARTNER"),

    INTERNAL("INTERNAL");

    private final String code;
}
